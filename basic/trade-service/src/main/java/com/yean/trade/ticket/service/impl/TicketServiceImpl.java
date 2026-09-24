package com.yean.trade.ticket.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yean.trade.message.service.LocalMessageService;
import com.yean.trade.message.service.impl.LocalMessageServiceImpl;
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
import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.common.lock.RedisBizLock;
import com.yean.common.operator.Operator;
import com.yean.common.order.OrderVO;
import com.yean.common.result.PageResult;
import com.yean.trade.order.service.OrderService;
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
    private final TicketMapper ticketMapper;
    private final TicketOperLogMapper ticketOperLogMapper;
    private final LocalMessageService localMessageService;
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
        String aftersaleNo = nextAftersaleNo();
        TicketVO vo;
        try {
            vo = transactionTemplate.execute(status -> {
                Ticket row = new Ticket();
                row.setAftersaleNo(aftersaleNo);
                row.setOrderNo(order.getOrderNo());
                row.setUserId(order.getUserId());
                row.setMerchantId(order.getMerchantId());
                row.setType(dto.getType());
                row.setStatus(TicketStatus.PENDING.getCode());
                row.setSkuId(order.getSkuId());
                row.setSkuName(order.getSkuName());
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
                localMessageService.insert(aftersaleNo + ":APPLY", LocalMessageServiceImpl.TOPIC_NOTIFY,
                        "{\"aftersaleNo\":\"" + aftersaleNo + "\",\"event\":\"APPLY\"}");
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
        return operate(dto, operator, TicketAction.APPROVE, null);
    }

    @Override
    public TicketVO reject(TicketActionDTO dto, Operator operator) {
        return operate(dto, operator, TicketAction.REJECT, TicketStatus.REJECTED.getCode());
    }

    @Override
    public TicketVO withdraw(TicketActionDTO dto, Operator operator) {
        requireUserId(operator);
        if (operator.getRole() != null && !RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())
                && !RoleEnum.ADMIN.name().equalsIgnoreCase(operator.getRole())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
        return operate(dto, operator, TicketAction.WITHDRAW, TicketStatus.CANCELED.getCode());
    }

    @Override
    public TicketVO ship(TicketActionDTO dto, Operator operator) {
        if (dto.getLogisticsInfo() == null
                || !StringUtils.hasText(dto.getLogisticsInfo().getCompany())
                || !StringUtils.hasText(dto.getLogisticsInfo().getTrackingNo())) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "发货须填写快递公司与运单号");
        }
        return operate(dto, operator, TicketAction.SHIP, TicketStatus.WAIT_RECEIVE.getCode());
    }

    @Override
    public TicketVO receive(TicketActionDTO dto, Operator operator) {
        requireUserId(operator);
        return operate(dto, operator, TicketAction.RECEIVE, TicketStatus.WAIT_REFUND.getCode());
    }

    @Override
    public TicketVO refund(TicketActionDTO dto, Operator operator) {
        return operate(dto, operator, TicketAction.REFUND, TicketStatus.DONE.getCode());
    }

    private TicketVO operate(TicketActionDTO dto, Operator operator, TicketAction action, Integer nextStatusFixed) {
        requireUserId(operator);
        String opKey = "aftersale:op:" + dto.getAftersaleNo() + ":" + action.name();
        if (!tryIdempotent(opKey, OP_IDEMPOTENT_TTL)) {
            return toVO(requireAftersale(dto.getAftersaleNo()), false);
        }
        try {
            TicketVO vo = redisBizLock.execute(LOCK_PREFIX, dto.getAftersaleNo(), () ->
                    transactionTemplate.execute(status -> doOperate(dto, operator, action, nextStatusFixed)));
            if (vo == null) {
                throw new BizException(ErrorEnum.AFTERSALE_NOT_FOUND);
            }
            return vo;
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(opKey);
            throw e;
        }
    }

    private TicketVO doOperate(TicketActionDTO dto, Operator operator, TicketAction action, Integer nextStatusFixed) {
        Ticket row = requireAftersale(dto.getAftersaleNo());
        int before = row.getStatus() == null ? -1 : row.getStatus();
        int nextStatus = nextStatusFixed != null ? nextStatusFixed : resolveApproveNext(row);
        assertTransition(row, operator, action, before);
        LogisticsInfo logistics = action == TicketAction.SHIP ? dto.getLogisticsInfo() : null;
        RefundDetail refundDetail = null;
        if (action == TicketAction.REFUND) {
            refundDetail = dto.getRefundDetail() != null ? dto.getRefundDetail() : new RefundDetail();
            if (refundDetail.getAmount() == null) {
                refundDetail.setAmount(row.getAmount());
            }
            if (!StringUtils.hasText(refundDetail.getChannel())) {
                refundDetail.setChannel("原路退回");
            }
        }
        boolean release = releasesApplySlot(nextStatus);
        ticketMapper.updateStatus(row.getId(), nextStatus, release, logistics, refundDetail);
        if (release) {
            stringRedisTemplate.delete(applyIdemKey(row.getOrderNo(), row.getSkuId()));
        }
        insertLog(row.getAftersaleNo(), operator, action, before, nextStatus, dto.getRemark());
        localMessageService.insert(row.getAftersaleNo() + ":" + action.name(), LocalMessageServiceImpl.TOPIC_NOTIFY,
                "{\"aftersaleNo\":\"" + row.getAftersaleNo() + "\",\"event\":\"" + action.name() + "\"}");
        row.setStatus(nextStatus);
        if (logistics != null) {
            row.setLogisticsInfo(logistics);
        }
        if (refundDetail != null) {
            row.setRefundDetail(refundDetail);
        }
        return toVO(row, false);
    }

    private int resolveApproveNext(Ticket row) {
        TicketType type = TicketType.require(row.getType());
        // 仅退款跳过发货/收货，直接待退款
        if (type == TicketType.REFUND_ONLY) {
            return TicketStatus.WAIT_REFUND.getCode();
        }
        return TicketStatus.WAIT_SHIP.getCode();
    }

    private void assertTransition(Ticket row, Operator operator, TicketAction action, int before) {
        switch (action) {
            case APPROVE:
            case REJECT:
                if (before != TicketStatus.PENDING.getCode()) {
                    throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
                }
                assertCanAudit(row, operator);
                break;
            case WITHDRAW:
                if (before != TicketStatus.PENDING.getCode()) {
                    throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
                }
                if (!row.getUserId().equals(operator.getUserId()) && !isAdmin(operator)) {
                    throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
                }
                break;
            case SHIP:
                if (before != TicketStatus.WAIT_SHIP.getCode()) {
                    throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
                }
                assertCanShip(operator);
                break;
            case RECEIVE:
                if (before != TicketStatus.WAIT_RECEIVE.getCode()) {
                    throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
                }
                if (!row.getUserId().equals(operator.getUserId()) && !isAdmin(operator)) {
                    throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
                }
                break;
            case REFUND:
                if (before != TicketStatus.WAIT_REFUND.getCode()) {
                    throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
                }
                assertCanRefund(row, operator);
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

    private void assertCanAudit(Ticket row, Operator operator) {
        if (isAdmin(operator)) {
            return;
        }
        if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())
                && row.getMerchantId().equals(operator.getUserId())) {
            return;
        }
        throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
    }

    private void assertCanShip(Operator operator) {
        if (isAdmin(operator) || RoleEnum.WAREHOUSE.name().equalsIgnoreCase(operator.getRole())) {
            return;
        }
        throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
    }

    private void assertCanRefund(Ticket row, Operator operator) {
        if (isAdmin(operator)) {
            return;
        }
        if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())
                && row.getMerchantId().equals(operator.getUserId())) {
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
        vo.setSkuId(row.getSkuId());
        vo.setSkuName(row.getSkuName());
        vo.setNum(row.getNum());
        vo.setAmount(row.getAmount());
        vo.setReason(row.getReason());
        vo.setLogisticsInfo(row.getLogisticsInfo());
        vo.setRefundDetail(row.getRefundDetail());
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
