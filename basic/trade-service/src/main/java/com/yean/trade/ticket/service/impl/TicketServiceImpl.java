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
import com.yean.trade.ticket.dto.TicketDraftDTO;
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
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
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
        // 提交：先删同用户同单草稿，再新增正式单（非草稿原地升格）
        deleteDraftQuietly(operator.getUserId(), dto.getOrderNo(), dto.getSkuId());
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
    public TicketVO saveDraft(TicketDraftDTO dto, Operator operator) {
        requireUserId(operator);
        assertUserOrAdmin(operator);
        OrderVO order = requireOrder(dto.getOrderNo());
        validateDraft(order, dto, operator);
        if (ticketMapper.selectActiveByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId()) != null) {
            throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW.getCode(), "已有进行中售后，无法保存草稿");
        }
        Long merchantId = skuService.requireMerchantId(order.getSkuId());
        int type = dto.getType() == null ? TicketType.RETURN.getCode() : dto.getType();
        TicketType.require(type);
        int num = dto.getNum() == null ? 1 : dto.getNum();
        if (num > order.getNum()) {
            throw new BizException(ErrorEnum.AFTERSALE_NUM_INVALID);
        }
        BigDecimal amount = calcAmount(order, num);
        Long exchangeSkuId = type == TicketType.EXCHANGE.getCode() ? dto.getExchangeSkuId() : null;

        return transactionTemplate.execute(status -> {
            Ticket draft = ticketMapper.selectDraftByUserOrderSku(
                    operator.getUserId(), dto.getOrderNo(), dto.getSkuId());
            if (draft != null) {
                draft.setType(type);
                draft.setNum(num);
                draft.setAmount(amount);
                draft.setReason(dto.getReason());
                draft.setExchangeSkuId(exchangeSkuId);
                draft.setMerchantId(merchantId);
                draft.setSkuName(order.getSkuName());
                ticketMapper.updateById(draft);
                log.debug("draft updated, aftersaleNo={}", draft.getAftersaleNo());
                return toVO(draft, false);
            }
            Ticket row = new Ticket();
            row.setAftersaleNo(nextAftersaleNo());
            row.setOrderNo(order.getOrderNo());
            row.setUserId(operator.getUserId());
            row.setMerchantId(merchantId);
            row.setType(type);
            row.setStatus(TicketStatus.DRAFT.getCode());
            row.setSkuId(order.getSkuId());
            row.setSkuName(order.getSkuName());
            row.setExchangeSkuId(exchangeSkuId);
            row.setNum(num);
            row.setAmount(amount);
            row.setReason(dto.getReason());
            row.setActiveFlag(null);
            ticketMapper.insert(row);
            log.debug("draft created, aftersaleNo={}", row.getAftersaleNo());
            return toVO(row, false);
        });
    }

    @Override
    public List<TicketVO> listMyDrafts(Operator operator) {
        requireUserId(operator);
        assertUserOrAdmin(operator);
        List<Ticket> rows = ticketMapper.selectDraftsByUserId(operator.getUserId());
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(r -> toVO(r, false)).collect(Collectors.toList());
    }

    @Override
    public TicketVO getMyDraft(String orderNo, Long skuId, Operator operator) {
        requireUserId(operator);
        assertUserOrAdmin(operator);
        if (!StringUtils.hasText(orderNo) || skuId == null) {
            throw new BizException(ErrorEnum.PARAM_INVALID);
        }
        Ticket draft = ticketMapper.selectDraftByUserOrderSku(operator.getUserId(), orderNo, skuId);
        return draft == null ? null : toVO(draft, false);
    }

    @Override
    public void deleteDraft(String aftersaleNo, Operator operator) {
        requireUserId(operator);
        Ticket row = requireAftersale(aftersaleNo);
        if (!TicketStatus.isDraft(row.getStatus())) {
            throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW.getCode(), "仅草稿可删除");
        }
        assertDraftOwner(row, operator);
        ticketMapper.deleteById(row.getId());
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

    @Override
    public TicketVO timeoutReject(String aftersaleNo, String remark) {
        if (!StringUtils.hasText(aftersaleNo)) {
            throw new BizException(ErrorEnum.PARAM_INVALID);
        }
        Operator system = new Operator(0L, "SYSTEM", RoleEnum.ADMIN.name());
        return redisBizLock.execute(LOCK_PREFIX, aftersaleNo, () -> {
            Ticket row = requireAftersale(aftersaleNo);
            assertTicketMutable(row);
            int status = row.getStatus() == null ? -1 : row.getStatus();
            if (status != TicketStatus.PENDING.getCode()
                    && status != TicketStatus.WAIT_SHIP.getCode()
                    && status != TicketStatus.WAIT_REFUND.getCode()) {
                throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW.getCode(),
                        "仅待审核/待发货/待退款可超时关单");
            }
            int before = status;
            ProcessActionDTO processDto = new ProcessActionDTO();
            processDto.setBusinessKey(aftersaleNo);
            processDto.setUserId(system.getUserId());
            processDto.setOperator(system.getUsername());
            processDto.setRole(system.getRole());
            processDto.setRemark(remark);
            R.unwrap(processFeignClient.timeoutClose(processDto));

            int nextStatus = TicketStatus.REJECTED.getCode();
            return transactionTemplate.execute(tx -> {
                ticketMapper.updateStatus(row.getId(), nextStatus, true, null, null);
                stringRedisTemplate.delete(applyIdemKey(row.getOrderNo(), row.getSkuId()));
                insertLog(aftersaleNo, system, TicketAction.REJECT, before, nextStatus, remark);
                row.setStatus(nextStatus);
                row.setActiveFlag(null);
                aftersaleNotifyPublisher.publishTimeoutClose(row);
                return toVO(row, false);
            });
        });
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
        assertTicketMutable(row);
        assertBusinessPermission(row, operator, action);
        int before = row.getStatus() == null ? -1 : row.getStatus();

        final LogisticsInfo logistics = action == TicketAction.SHIP ? dto.getLogisticsInfo() : null;
        final RefundDetail refundDetail = buildRefundDetail(action, dto, row);
        TicketNodeStrategy strategy = strategyFactory.getStrategy(action);

        ProcessInstanceVO process = invokeProcess(dto, operator, action);
        final boolean processAdvanced = actionCompletesProcessTask(action);

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

        try {
            return transactionTemplate.execute(status -> {
                // 库存等副作用先于改状态；失败则本事务回滚，并由外层补偿流程节点
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
        } catch (RuntimeException e) {
            if (processAdvanced) {
                // G3：换货发货 process 已到待收货，库存不足 → ADMIN 回退到 warehouseShip
                safeCompensateProcess(row.getAftersaleNo(), e);
            }
            throw e;
        }
    }

    private ProcessInstanceVO invokeProcess(TicketActionDTO dto, Operator operator, TicketAction action) {
        ProcessActionDTO processDto = buildProcessAction(dto, operator, action);
        switch (action) {
            case REJECT:
                return R.unwrap(processFeignClient.reject(processDto));
            case WITHDRAW:
                return R.unwrap(processFeignClient.withdraw(processDto));
            case ROLLBACK:
                return R.unwrap(processFeignClient.rollback(processDto));
            default:
                return R.unwrap(processFeignClient.complete(processDto));
        }
    }

    private static boolean actionCompletesProcessTask(TicketAction action) {
        return action != TicketAction.REJECT
                && action != TicketAction.WITHDRAW
                && action != TicketAction.ROLLBACK;
    }

    /** 流程已 complete 后本地失败：以 ADMIN 回退到上一节点，与售后本地状态对齐 */
    private void safeCompensateProcess(String aftersaleNo, Exception cause) {
        try {
            ProcessActionDTO compensate = new ProcessActionDTO();
            compensate.setBusinessKey(aftersaleNo);
            compensate.setOperator("system");
            compensate.setRole(RoleEnum.ADMIN.name());
            compensate.setRemark("本地事务失败，补偿回滚流程");
            R.unwrap(processFeignClient.rollback(compensate));
            log.warn("process compensated after local failure, aftersaleNo={}", aftersaleNo, cause);
        } catch (Exception compensateEx) {
            log.error("process compensate FAILED, aftersaleNo={}, original={}",
                    aftersaleNo, cause.toString(), compensateEx);
        }
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
            refundDetail.setChannel("ORIGINAL");
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

    /** T2：DONE / REJECTED / CANCELED 统一只读；草稿禁止走审核/发货等流程操作 */
    private static void assertTicketMutable(Ticket row) {
        if (TicketStatus.isDraft(row.getStatus())) {
            throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW.getCode(), "草稿请先提交后再操作");
        }
        if (TicketStatus.isTerminal(row.getStatus())) {
            throw new BizException(ErrorEnum.PROCESS_ENDED.getCode(), "售后单已终态，禁止修改");
        }
    }

    private void deleteDraftQuietly(Long userId, String orderNo, Long skuId) {
        Ticket draft = ticketMapper.selectDraftByUserOrderSku(userId, orderNo, skuId);
        if (draft != null) {
            ticketMapper.deleteById(draft.getId());
            log.info("draft removed before submit, aftersaleNo={}", draft.getAftersaleNo());
        }
    }

    private void assertUserOrAdmin(Operator operator) {
        if (isAdmin(operator)) {
            return;
        }
        if (operator.getRole() == null || !RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
    }

    private void assertDraftOwner(Ticket row, Operator operator) {
        if (isAdmin(operator)) {
            return;
        }
        if (!row.getUserId().equals(operator.getUserId())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
    }

    /** 草稿校验：弱于正式申请；换货可不填 exchangeSkuId */
    private void validateDraft(OrderVO order, TicketDraftDTO dto, Operator operator) {
        if (!isAdmin(operator) && !order.getUserId().equals(operator.getUserId())) {
            throw new BizException(ErrorEnum.ORDER_NOT_OWNER);
        }
        if (!order.getSkuId().equals(dto.getSkuId())) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "skuId 与订单不符");
        }
        if (dto.getType() != null) {
            TicketType.require(dto.getType());
        }
        if (dto.getNum() != null && dto.getNum() > order.getNum()) {
            throw new BizException(ErrorEnum.AFTERSALE_NUM_INVALID);
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
                if (!isWarehouse(operator)) {
                    throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
                }
                break;
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

    /**
     * T1 列表数据权限：强制覆盖查询条件，禁止客户端用 userId/merchantId 越权扩大范围。
     * USER→本人；MERCHANT→本商家；WAREHOUSE/ADMIN→不强制归属（仓库可看待发货池）；未知角色拒绝。
     */
    private void applyRoleFilter(TicketQueryDTO cond, Operator operator) {
        requireUserId(operator);
        if (isAdmin(operator) || isWarehouse(operator)) {
            return;
        }
        if (RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())) {
            cond.setUserId(operator.getUserId());
            cond.setMerchantId(null);
            return;
        }
        if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())) {
            cond.setMerchantId(operator.getUserId());
            cond.setUserId(null);
            return;
        }
        throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
    }

    /** T1 详情数据权限：缺省拒绝；草稿仅本人/ADMIN 可见 */
    private void assertCanView(Ticket row, Operator operator) {
        requireUserId(operator);
        if (TicketStatus.isDraft(row.getStatus())) {
            assertDraftOwner(row, operator);
            return;
        }
        if (isAdmin(operator) || isWarehouse(operator)) {
            return;
        }
        if (RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())) {
            if (!row.getUserId().equals(operator.getUserId())) {
                throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
            }
            return;
        }
        if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())) {
            if (!row.getMerchantId().equals(operator.getUserId())) {
                throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
            }
            return;
        }
        throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
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
        vo.setDraft(TicketStatus.isDraft(row.getStatus()));
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
