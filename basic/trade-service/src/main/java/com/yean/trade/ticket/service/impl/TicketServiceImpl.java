package com.yean.trade.ticket.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.common.lock.RedisBizLock;
import com.yean.common.dto.Operator;
import com.yean.trade.order.dto.OrderVO;
import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessDefKey;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessStartDTO;
import com.yean.common.dto.process.ProcessTaskKeys;
import com.yean.common.result.PageResult;
import com.yean.common.result.R;
import com.yean.common.rpc.ProcessFeignClient;
import com.yean.trade.notify.AftersaleNotifyPublisher;
import com.yean.trade.order.service.OrderService;
import com.yean.trade.sku.service.SkuService;
import com.yean.trade.ticket.dto.LogisticsInfo;
import com.yean.trade.ticket.dto.RefundDetail;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.dto.TicketActionDTO;
import com.yean.trade.ticket.dto.TicketApplyDTO;
import com.yean.trade.ticket.dto.TicketLogVO;
import com.yean.trade.ticket.dto.TicketOperLog;
import com.yean.trade.ticket.dto.TicketQueryDTO;
import com.yean.trade.ticket.dto.TicketVO;
import com.yean.trade.ticket.enums.TicketAction;
import com.yean.trade.ticket.enums.TicketStatus;
import com.yean.trade.ticket.enums.TicketType;
import com.yean.trade.ticket.mapper.TicketMapper;
import com.yean.trade.ticket.mapper.TicketOperLogMapper;
import com.yean.trade.ticket.service.TicketService;
import com.yean.trade.ticket.strategy.TicketNodeContext;
import com.yean.trade.ticket.strategy.TicketNodeStrategy;
import com.yean.trade.ticket.strategy.TicketNodeStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final Duration APPLY_IDEMPOTENT_TTL = Duration.ofDays(7);
    private static final Duration OP_IDEMPOTENT_TTL = Duration.ofHours(24);
    private static final String LOCK_PREFIX = "lock:aftersale:";

    private final OrderService orderService;
    private final SkuService skuService;
    private final ProcessFeignClient processFeignClient;
    private final TicketNodeStrategyFactory strategyFactory;
    private final TicketMapper ticketMapper;
    private final TicketOperLogMapper ticketOperLogMapper;
    private final AftersaleNotifyPublisher aftersaleNotifyPublisher;
    private final RedisBizLock redisBizLock;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public TicketVO apply(TicketApplyDTO dto, Operator operator) {
        requireUserId(operator);
        TicketType.require(dto.getType());
        Ticket exist = ticketMapper.selectActiveByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
        if (exist != null) {
            return toVO(exist, false);
        }
        String idemKey = applyIdemKey(dto.getOrderNo(), dto.getSkuId());
        if (!tryIdempotent(idemKey, APPLY_IDEMPOTENT_TTL)) {
            Ticket again = ticketMapper.selectActiveByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
            if (again != null) {
                return toVO(again, false);
            }
            throw new BizException(ErrorEnum.CONFLICT);
        }
        OrderVO order = requireOrder(dto.getOrderNo());
        validateApply(order, dto, operator);
        Long merchantId = skuService.requireMerchantId(order.getSkuId());
        if (dto.getExchangeSkuId() != null) {
            Long exchangeMerchant = skuService.requireMerchantId(dto.getExchangeSkuId());
            if (!merchantId.equals(exchangeMerchant)) {
                throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "换货目标 SKU 须属于同一商家");
            }
        }
        String aftersaleNo = nextAftersaleNo();
        TicketVO vo;
        try {
            vo = transactionTemplate.execute(status -> {
                Ticket row = new Ticket();
                row.setAftersaleNo(aftersaleNo);
                row.setOrderNo(order.getOrderNo());
                row.setUserId(order.getUserId());
                // 商家以商品档案为准（非订单上的冗余字段）
                row.setMerchantId(merchantId);
                row.setType(dto.getType());
                row.setStatus(TicketStatus.PENDING.getCode());
                row.setSkuId(order.getSkuId());
                row.setSkuName(order.getSkuName());
                row.setExchangeSkuId(dto.getExchangeSkuId());
                row.setNum(dto.getNum());
                row.setAmount(calcAmount(order, dto.getNum()));
                row.setReason(dto.getReason());
                row.setActiveFlag(1);
                try {
                    ticketMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    return null;
                }
                insertLog(aftersaleNo, operator, TicketAction.APPLY, null, TicketStatus.PENDING.getCode(), dto.getReason());
                return toVO(row, false);
            });
        } finally {
            Ticket created = ticketMapper.selectActiveByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
            if (created == null) {
                stringRedisTemplate.delete(idemKey);
            }
        }
        if (vo == null) {
            Ticket again = ticketMapper.selectActiveByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
            if (again != null) {
                return toVO(again, false);
            }
            throw new BizException(ErrorEnum.CONFLICT);
        }
        // 流程启动在本地事务外：失败则补偿删单，避免无流程的脏售后单
        try {
            ProcessStartDTO start = new ProcessStartDTO();
            start.setBusinessKey(aftersaleNo);
            start.setProcessDefKey(ProcessDefKey.fromTicketType(dto.getType()).getKey());
            start.setApplicantUserId(order.getUserId());
            start.setOperator(operator.getUsername());
            start.setRole(operator.getRole());
            start.setRemark(dto.getReason());
            ProcessInstanceVO process = R.unwrap(processFeignClient.start(start));
            Ticket row = requireAftersale(aftersaleNo);
            row.setProcessInstanceId(process.getProcessInstanceId());
            // 流程已起：同事务写 processInstanceId + outbox，随后由 Sender 投递
            transactionTemplate.execute(status -> {
                ticketMapper.updateById(row);
                aftersaleNotifyPublisher.publish(row, TicketAction.APPLY);
                return null;
            });
            vo.setProcessInstanceId(process.getProcessInstanceId());
        } catch (RuntimeException e) {
            ticketMapper.deleteById(requireAftersale(aftersaleNo).getId());
            stringRedisTemplate.delete(idemKey);
            throw e;
        }
        return vo;
    }

    @Override
    public PageResult<TicketVO> page(TicketQueryDTO query, Operator operator) {
        TicketQueryDTO cond = query == null ? new TicketQueryDTO() : query;
        applyRoleFilter(cond, operator);
        long pageNo = cond.pageOrDefault();
        long pageSize = cond.sizeOrDefault();
        IPage<Ticket> result = ticketMapper.selectListByQuery(new Page<>(pageNo, pageSize), cond);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(row -> toVO(row, false)).collect(Collectors.toList()));
    }

    @Override
    public TicketVO detail(String aftersaleNo, Operator operator) {
        Ticket row = requireAftersale(aftersaleNo);
        assertCanView(row, operator);
        TicketVO vo = toVO(row, true);
        vo.setLogs(ticketOperLogMapper.selectByAftersaleNo(aftersaleNo).stream()
                .map(this::toLogVO)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public TicketVO approve(TicketActionDTO dto, Operator operator) {
        return operate(dto, operator, TicketAction.APPROVE);
    }

    @Override
    public TicketVO reject(TicketActionDTO dto, Operator operator) {
        return operate(dto, operator, TicketAction.REJECT);
    }

    @Override
    public TicketVO withdraw(TicketActionDTO dto, Operator operator) {
        requireUserId(operator);
        if (operator.getRole() != null && !RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())
                && !RoleEnum.ADMIN.name().equalsIgnoreCase(operator.getRole())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
        Ticket row = requireAftersale(dto.getAftersaleNo());
        int status = row.getStatus() == null ? -1 : row.getStatus();
        // 待审核 / 待仓库发货（尚未发货、无库存副作用）可撤销
        if (status != TicketStatus.PENDING.getCode() && status != TicketStatus.WAIT_SHIP.getCode()) {
            throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW.getCode(),
                    "仅待审核或待仓库发货可撤销申请");
        }
        return operate(dto, operator, TicketAction.WITHDRAW);
    }

    @Override
    public TicketVO ship(TicketActionDTO dto, Operator operator) {
        if (dto.getLogisticsInfo() == null
                || !StringUtils.hasText(dto.getLogisticsInfo().getCompany())
                || !StringUtils.hasText(dto.getLogisticsInfo().getTrackingNo())) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "发货须填写快递公司与运单号");
        }
        return operate(dto, operator, TicketAction.SHIP);
    }

    @Override
    public TicketVO receive(TicketActionDTO dto, Operator operator) {
        requireUserId(operator);
        return operate(dto, operator, TicketAction.RECEIVE);
    }

    @Override
    public TicketVO refund(TicketActionDTO dto, Operator operator) {
        return operate(dto, operator, TicketAction.REFUND);
    }

    @Override
    public TicketVO rollback(TicketActionDTO dto, Operator operator) {
        return operate(dto, operator, TicketAction.ROLLBACK);
    }

    private TicketVO operate(TicketActionDTO dto, Operator operator, TicketAction action) {
        requireUserId(operator);
        String opKey = "aftersale:op:" + dto.getAftersaleNo() + ":" + action.name();
        if (!tryIdempotent(opKey, OP_IDEMPOTENT_TTL)) {
            return toVO(requireAftersale(dto.getAftersaleNo()), false);
        }
        try {
            TicketVO vo = redisBizLock.execute(LOCK_PREFIX, dto.getAftersaleNo(), () ->
                    doOperateWithProcess(dto, operator, action));
            if (vo == null) {
                throw new BizException(ErrorEnum.AFTERSALE_NOT_FOUND);
            }
            return vo;
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(opKey);
            throw e;
        }
    }

    private TicketVO doOperateWithProcess(TicketActionDTO dto, Operator operator, TicketAction action) {
        Ticket row = requireAftersale(dto.getAftersaleNo());
        assertNotTerminal(row);
        assertBusinessPermission(row, operator, action);
        int before = row.getStatus() == null ? -1 : row.getStatus();

        ProcessActionDTO processDto = buildProcessAction(dto, operator, action);
        ProcessInstanceVO process;
        switch (action) {
            case REJECT:
                process = R.unwrap(processFeignClient.reject(processDto));
                break;
            case WITHDRAW:
                process = R.unwrap(processFeignClient.withdraw(processDto));
                break;
            case ROLLBACK:
                process = R.unwrap(processFeignClient.rollback(processDto));
                break;
            default:
                process = R.unwrap(processFeignClient.complete(processDto));
                break;
        }

        final LogisticsInfo logistics = action == TicketAction.SHIP ? dto.getLogisticsInfo() : null;
        final RefundDetail refundDetail = buildRefundDetail(action, dto, row);
        TicketNodeStrategy strategy = strategyFactory.getStrategy(action);
        TicketNodeContext ctx = TicketNodeContext.builder()
                .row(row)
                .dto(dto)
                .operator(operator)
                .action(action)
                .process(process)
                .logistics(logistics)
                .refundDetail(refundDetail)
                .build();
        final int nextStatus = strategy.resolveNextStatus(ctx);

        TicketVO vo = transactionTemplate.execute(status -> {
            // 策略副作用（库存等）先于改状态；失败则本地事务回滚
            strategy.afterProcess(ctx);
            boolean release = releasesApplySlot(nextStatus);
            ticketMapper.updateStatus(row.getId(), nextStatus, release, logistics, refundDetail);
            if (release) {
                stringRedisTemplate.delete(applyIdemKey(row.getOrderNo(), row.getSkuId()));
            }
            insertLog(row.getAftersaleNo(), operator, action, before, nextStatus, dto.getRemark());
            row.setStatus(nextStatus);
            if (logistics != null) {
                row.setLogisticsInfo(logistics);
            }
            if (refundDetail != null) {
                row.setRefundDetail(refundDetail);
            }
            aftersaleNotifyPublisher.publish(row, action);
            return toVO(row, false);
        });
        return vo;
    }

    private static RefundDetail buildRefundDetail(TicketAction action, TicketActionDTO dto, Ticket row) {
        if (action != TicketAction.REFUND) {
            return null;
        }
        RefundDetail refundDetail = dto.getRefundDetail() != null ? dto.getRefundDetail() : new RefundDetail();
        if (refundDetail.getAmount() == null) {
            refundDetail.setAmount(row.getAmount());
        }
        if (!StringUtils.hasText(refundDetail.getChannel())) {
            refundDetail.setChannel("原路退回");
        }
        return refundDetail;
    }

    private ProcessActionDTO buildProcessAction(TicketActionDTO dto, Operator operator, TicketAction action) {
        ProcessActionDTO processDto = new ProcessActionDTO();
        processDto.setBusinessKey(dto.getAftersaleNo());
        processDto.setExpectedTaskKey(expectedTaskKey(action));
        processDto.setUserId(operator.getUserId());
        processDto.setOperator(operator.getUsername());
        processDto.setRole(operator.getRole());
        processDto.setRemark(dto.getRemark());
        return processDto;
    }

    private static String expectedTaskKey(TicketAction action) {
        switch (action) {
            case APPROVE:
            case REJECT:
                return ProcessTaskKeys.MERCHANT_APPROVE;
            case WITHDRAW:
                // 流程侧按当前节点校验可撤销任务；此处不强制 expected
                return null;
            case SHIP:
                return ProcessTaskKeys.WAREHOUSE_SHIP;
            case RECEIVE:
                return ProcessTaskKeys.USER_RECEIVE;
            case REFUND:
                return ProcessTaskKeys.MERCHANT_REFUND;
            case ROLLBACK:
                return null;
            default:
                throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
        }
    }

    private static void assertNotTerminal(Ticket row) {
        int status = row.getStatus() == null ? -1 : row.getStatus();
        if (status == TicketStatus.DONE.getCode()
                || status == TicketStatus.REJECTED.getCode()
                || status == TicketStatus.CANCELED.getCode()) {
            throw new BizException(ErrorEnum.PROCESS_ENDED);
        }
    }

    /** 业务侧归属校验；节点角色由 process-service 候选组再验一层 */
    private void assertBusinessPermission(Ticket row, Operator operator, TicketAction action) {
        if (isAdmin(operator)) {
            return;
        }
        switch (action) {
            case APPROVE:
            case REJECT:
            case REFUND:
                if (!RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())
                        || !row.getMerchantId().equals(operator.getUserId())) {
                    throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
                }
                break;
            case WITHDRAW:
            case RECEIVE:
                if (!row.getUserId().equals(operator.getUserId())) {
                    throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
                }
                break;
            case SHIP:
            case ROLLBACK:
                break;
            default:
                throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
        }
    }

    private static boolean releasesApplySlot(int status) {
        return status == TicketStatus.REJECTED.getCode() || status == TicketStatus.CANCELED.getCode();
    }

    private static BigDecimal calcAmount(OrderVO order, Integer num) {
        if (num == null || num.equals(order.getNum())) {
            return order.getAmount();
        }
        return order.getAmount()
                .multiply(BigDecimal.valueOf(num))
                .divide(BigDecimal.valueOf(order.getNum()), 2, RoundingMode.HALF_UP);
    }

    private void validateApply(OrderVO order, TicketApplyDTO dto, Operator operator) {
        if (!isAdmin(operator) && !order.getUserId().equals(operator.getUserId())) {
            throw new BizException(ErrorEnum.ORDER_NOT_OWNER);
        }
        if (operator.getRole() != null && !RoleEnum.USER.name().equalsIgnoreCase(operator.getRole()) && !isAdmin(operator)) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
        if (order.getPayStatus() == null || order.getPayStatus() != 1) {
            throw new BizException(ErrorEnum.ORDER_UNPAID);
        }
        if (order.getStatus() == null || order.getStatus() == 4) {
            throw new BizException(ErrorEnum.ORDER_STATUS_NOT_ALLOW);
        }
        TicketType type = TicketType.require(dto.getType());
        if (type != TicketType.REFUND_ONLY && order.getStatus() == 1) {
            throw new BizException(ErrorEnum.ORDER_STATUS_NOT_ALLOW);
        }
        if (!order.getSkuId().equals(dto.getSkuId())) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "skuId 与订单不符");
        }
        if (dto.getNum() > order.getNum()) {
            throw new BizException(ErrorEnum.AFTERSALE_NUM_INVALID);
        }
        if (type == TicketType.EXCHANGE) {
            if (dto.getExchangeSkuId() == null) {
                throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "换货须指定 exchangeSkuId");
            }
            if (dto.getExchangeSkuId().equals(dto.getSkuId())) {
                throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "换货目标 SKU 不能与原商品相同");
            }
            skuService.getBySkuId(dto.getExchangeSkuId());
        } else if (dto.getExchangeSkuId() != null) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "非换货单不要传 exchangeSkuId");
        }
    }

    private void applyRoleFilter(TicketQueryDTO cond, Operator operator) {
        if (operator == null || operator.getUserId() == null) {
            return;
        }
        if (RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())) {
            cond.setUserId(operator.getUserId());
        } else if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())) {
            cond.setMerchantId(operator.getUserId());
        }
    }

    private void assertCanView(Ticket row, Operator operator) {
        if (operator == null || operator.getUserId() == null || isAdmin(operator)
                || isWarehouse(operator)) {
            return;
        }
        if (RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())
                && !row.getUserId().equals(operator.getUserId())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
        if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())
                && !row.getMerchantId().equals(operator.getUserId())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
    }

    private OrderVO requireOrder(String orderNo) {
        return orderService.getByOrderNo(orderNo);
    }

    private Ticket requireAftersale(String aftersaleNo) {
        Ticket row = ticketMapper.selectByAftersaleNo(aftersaleNo);
        if (row == null) {
            throw new BizException(ErrorEnum.AFTERSALE_NOT_FOUND);
        }
        return row;
    }

    private void insertLog(String aftersaleNo, Operator operator, TicketAction action,
                           Integer before, int after, String remark) {
        TicketOperLog log = new TicketOperLog();
        log.setAftersaleNo(aftersaleNo);
        log.setOperatorId(operator.getUserId());
        log.setOperatorName(operator.getUsername());
        log.setAction(action.name());
        log.setBeforeStatus(before);
        log.setAfterStatus(after);
        log.setRemark(remark);
        ticketOperLogMapper.insert(log);
    }

    private String nextAftersaleNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "aftersale:seq:" + day;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            stringRedisTemplate.expire(key, Duration.ofHours(48));
        }
        return "AS" + day + String.format("%06d", seq == null ? 1 : seq);
    }

    private static String applyIdemKey(String orderNo, Long skuId) {
        return "aftersale:idem:" + orderNo + ":" + skuId;
    }

    private boolean tryIdempotent(String key, Duration ttl) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    private static void requireUserId(Operator operator) {
        if (operator == null || operator.getUserId() == null) {
            throw new BizException(ErrorEnum.UNAUTHORIZED);
        }
    }

    private static boolean isAdmin(Operator operator) {
        return operator.getRole() != null && RoleEnum.ADMIN.name().equalsIgnoreCase(operator.getRole());
    }

    private static boolean isWarehouse(Operator operator) {
        return operator.getRole() != null
                && RoleEnum.WAREHOUSE.name().equalsIgnoreCase(operator.getRole());
    }

    private TicketVO toVO(Ticket row, boolean withLogs) {
        TicketVO vo = new TicketVO();
        vo.setAftersaleNo(row.getAftersaleNo());
        vo.setOrderNo(row.getOrderNo());
        vo.setUserId(row.getUserId());
        vo.setMerchantId(row.getMerchantId());
        vo.setType(row.getType());
        vo.setStatus(row.getStatus());
        vo.setSkuId(row.getSkuId());
        vo.setSkuName(row.getSkuName());
        vo.setExchangeSkuId(row.getExchangeSkuId());
        vo.setNum(row.getNum());
        vo.setAmount(row.getAmount());
        vo.setReason(row.getReason());
        vo.setLogisticsInfo(row.getLogisticsInfo());
        vo.setRefundDetail(row.getRefundDetail());
        vo.setProcessInstanceId(row.getProcessInstanceId());
        vo.setCreateTime(row.getCreateTime());
        if (!withLogs) {
            vo.setLogs(null);
        }
        return vo;
    }

    private TicketLogVO toLogVO(TicketOperLog log) {
        TicketLogVO vo = new TicketLogVO();
        vo.setAction(log.getAction());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(log.getOperatorName());
        vo.setBeforeStatus(log.getBeforeStatus());
        vo.setAfterStatus(log.getAfterStatus());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
