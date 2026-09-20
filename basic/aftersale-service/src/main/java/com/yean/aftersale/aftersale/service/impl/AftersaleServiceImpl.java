package com.yean.aftersale.aftersale.service.impl;

import com.yean.aftersale.aftersale.dto.AftersaleActionDTO;
import com.yean.aftersale.aftersale.dto.AftersaleApplyDTO;
import com.yean.aftersale.aftersale.dto.AftersaleLogVO;
import com.yean.aftersale.aftersale.dto.AftersaleOperLog;
import com.yean.aftersale.aftersale.dto.AftersaleOrder;
import com.yean.aftersale.aftersale.dto.AftersaleQueryDTO;
import com.yean.aftersale.aftersale.dto.AftersaleVO;
import com.yean.aftersale.aftersale.enums.AftersaleAction;
import com.yean.aftersale.aftersale.enums.AftersaleStatus;
import com.yean.aftersale.aftersale.enums.AftersaleType;
import com.yean.aftersale.aftersale.mapper.AftersaleOperLogMapper;
import com.yean.aftersale.aftersale.mapper.AftersaleOrderMapper;
import com.yean.aftersale.aftersale.service.AftersaleService;
import com.yean.aftersale.config.AftersaleLock;
import com.yean.aftersale.config.Operator;
import com.yean.aftersale.message.service.LocalMessageService;
import com.yean.aftersale.message.service.impl.LocalMessageServiceImpl;
import com.yean.aftersale.order.dto.OrderInfo;
import com.yean.aftersale.order.mapper.OrderMapper;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AftersaleServiceImpl implements AftersaleService {

    private static final Duration APPLY_IDEMPOTENT_TTL = Duration.ofDays(7);
    private static final Duration OP_IDEMPOTENT_TTL = Duration.ofHours(24);

    private final OrderMapper orderMapper;
    private final AftersaleOrderMapper aftersaleOrderMapper;
    private final AftersaleOperLogMapper aftersaleOperLogMapper;
    private final LocalMessageService localMessageService;
    private final AftersaleLock aftersaleLock;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AftersaleVO apply(AftersaleApplyDTO dto, Operator operator) {
        requireUserId(operator);
        AftersaleType.require(dto.getType());
        AftersaleOrder exist = aftersaleOrderMapper.selectByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
        if (exist != null) {
            return toVO(exist, false);
        }
        String idemKey = "aftersale:idem:" + dto.getOrderNo() + ":" + dto.getSkuId();
        if (!tryIdempotent(idemKey, APPLY_IDEMPOTENT_TTL)) {
            AftersaleOrder again = aftersaleOrderMapper.selectByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
            if (again != null) {
                return toVO(again, false);
            }
            throw new BizException(ErrorEnum.CONFLICT);
        }
        OrderInfo order = requireOrder(dto.getOrderNo());
        validateApply(order, dto, operator);
        String aftersaleNo = nextAftersaleNo();
        AftersaleVO vo;
        try {
            vo = transactionTemplate.execute(status -> {
                AftersaleOrder row = new AftersaleOrder();
                row.setAftersaleNo(aftersaleNo);
                row.setOrderNo(order.getOrderNo());
                row.setUserId(order.getUserId());
                row.setMerchantId(order.getMerchantId());
                row.setType(dto.getType());
                row.setStatus(AftersaleStatus.PENDING.getCode());
                row.setSkuId(order.getSkuId());
                row.setSkuName(order.getSkuName());
                row.setNum(dto.getNum());
                row.setAmount(order.getAmount());
                row.setReason(dto.getReason());
                row.setLogisticsInfo(dto.getLogisticsInfo());
                try {
                    aftersaleOrderMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    return null;
                }
                insertLog(aftersaleNo, operator, AftersaleAction.APPLY, null, AftersaleStatus.PENDING.getCode(), dto.getReason());
                localMessageService.insert(aftersaleNo + ":APPLY", LocalMessageServiceImpl.TOPIC_NOTIFY,
                        "{\"aftersaleNo\":\"" + aftersaleNo + "\",\"event\":\"APPLY\"}");
                return toVO(row, false);
            });
        } finally {
            AftersaleOrder created = aftersaleOrderMapper.selectByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
            if (created == null) {
                stringRedisTemplate.delete(idemKey);
            }
        }
        if (vo == null) {
            AftersaleOrder again = aftersaleOrderMapper.selectByOrderNoAndSkuId(dto.getOrderNo(), dto.getSkuId());
            if (again != null) {
                return toVO(again, false);
            }
            throw new BizException(ErrorEnum.CONFLICT);
        }
        return vo;
    }

    @Override
    public List<AftersaleVO> page(AftersaleQueryDTO query, Operator operator) {
        AftersaleQueryDTO cond = query == null ? new AftersaleQueryDTO() : query;
        applyRoleFilter(cond, operator);
        return aftersaleOrderMapper.selectListByQuery(cond).stream()
                .map(row -> toVO(row, false))
                .collect(Collectors.toList());
    }

    @Override
    public AftersaleVO detail(String aftersaleNo, Operator operator) {
        AftersaleOrder row = requireAftersale(aftersaleNo);
        assertCanView(row, operator);
        AftersaleVO vo = toVO(row, true);
        vo.setLogs(aftersaleOperLogMapper.selectByAftersaleNo(aftersaleNo).stream()
                .map(this::toLogVO)
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public AftersaleVO approve(AftersaleActionDTO dto, Operator operator) {
        return operate(dto, operator, AftersaleAction.APPROVE, AftersaleStatus.APPROVED.getCode());
    }

    @Override
    public AftersaleVO reject(AftersaleActionDTO dto, Operator operator) {
        return operate(dto, operator, AftersaleAction.REJECT, AftersaleStatus.REJECTED.getCode());
    }

    @Override
    public AftersaleVO withdraw(AftersaleActionDTO dto, Operator operator) {
        requireUserId(operator);
        if (operator.getRole() != null && !RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())
                && !RoleEnum.ADMIN.name().equalsIgnoreCase(operator.getRole())) {
            throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
        }
        return operate(dto, operator, AftersaleAction.WITHDRAW, AftersaleStatus.REJECTED.getCode());
    }

    private AftersaleVO operate(AftersaleActionDTO dto, Operator operator, AftersaleAction action, int nextStatus) {
        requireUserId(operator);
        String opKey = "aftersale:op:" + dto.getAftersaleNo() + ":" + action.name();
        if (!tryIdempotent(opKey, OP_IDEMPOTENT_TTL)) {
            return toVO(requireAftersale(dto.getAftersaleNo()), false);
        }
        try {
            AftersaleVO vo = aftersaleLock.execute(dto.getAftersaleNo(), () ->
                    transactionTemplate.execute(status -> doOperate(dto, operator, action, nextStatus)));
            if (vo == null) {
                throw new BizException(ErrorEnum.AFTERSALE_NOT_FOUND);
            }
            return vo;
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(opKey);
            throw e;
        }
    }

    private AftersaleVO doOperate(AftersaleActionDTO dto, Operator operator, AftersaleAction action, int nextStatus) {
        AftersaleOrder row = requireAftersale(dto.getAftersaleNo());
        if (row.getStatus() == null || row.getStatus() != AftersaleStatus.PENDING.getCode()) {
            throw new BizException(ErrorEnum.AFTERSALE_STATUS_NOT_ALLOW);
        }
        if (action == AftersaleAction.WITHDRAW) {
            if (!row.getUserId().equals(operator.getUserId()) && !isAdmin(operator)) {
                throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
            }
        } else {
            assertCanAudit(row, operator);
        }
        int before = row.getStatus();
        row.setStatus(nextStatus);
        aftersaleOrderMapper.updateById(row);
        insertLog(row.getAftersaleNo(), operator, action, before, nextStatus, dto.getRemark());
        localMessageService.insert(row.getAftersaleNo() + ":" + action.name(), LocalMessageServiceImpl.TOPIC_NOTIFY,
                "{\"aftersaleNo\":\"" + row.getAftersaleNo() + "\",\"event\":\"" + action.name() + "\"}");
        return toVO(row, false);
    }

    private void validateApply(OrderInfo order, AftersaleApplyDTO dto, Operator operator) {
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
        AftersaleType type = AftersaleType.require(dto.getType());
        if (type != AftersaleType.REFUND_ONLY && order.getStatus() == 1) {
            throw new BizException(ErrorEnum.ORDER_STATUS_NOT_ALLOW);
        }
        if (!order.getSkuId().equals(dto.getSkuId())) {
            throw new BizException(ErrorEnum.PARAM_INVALID.getCode(), "skuId 与订单不符");
        }
        if (dto.getNum() > order.getNum()) {
            throw new BizException(ErrorEnum.AFTERSALE_NUM_INVALID);
        }
    }

    private void applyRoleFilter(AftersaleQueryDTO cond, Operator operator) {
        if (operator == null || operator.getUserId() == null) {
            return;
        }
        if (RoleEnum.USER.name().equalsIgnoreCase(operator.getRole())) {
            cond.setUserId(operator.getUserId());
        } else if (RoleEnum.MERCHANT.name().equalsIgnoreCase(operator.getRole())) {
            cond.setMerchantId(operator.getUserId());
        }
    }

    private void assertCanView(AftersaleOrder row, Operator operator) {
        if (operator == null || operator.getUserId() == null || isAdmin(operator)
                || isWarehouseOrFinance(operator)) {
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

    private void assertCanAudit(AftersaleOrder row, Operator operator) {
        String role = operator.getRole();
        if (isAdmin(operator) || isWarehouseOrFinance(operator)) {
            return;
        }
        if (RoleEnum.MERCHANT.name().equalsIgnoreCase(role) && row.getMerchantId().equals(operator.getUserId())) {
            return;
        }
        throw new BizException(ErrorEnum.AFTERSALE_NO_PERMISSION);
    }

    private OrderInfo requireOrder(String orderNo) {
        OrderInfo order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorEnum.ORDER_NOT_FOUND);
        }
        return order;
    }

    private AftersaleOrder requireAftersale(String aftersaleNo) {
        AftersaleOrder row = aftersaleOrderMapper.selectByAftersaleNo(aftersaleNo);
        if (row == null) {
            throw new BizException(ErrorEnum.AFTERSALE_NOT_FOUND);
        }
        return row;
    }

    private void insertLog(String aftersaleNo, Operator operator, AftersaleAction action,
                           Integer before, int after, String remark) {
        AftersaleOperLog log = new AftersaleOperLog();
        log.setAftersaleNo(aftersaleNo);
        log.setOperatorId(operator.getUserId());
        log.setOperatorName(operator.getUsername());
        log.setAction(action.name());
        log.setBeforeStatus(before);
        log.setAfterStatus(after);
        log.setRemark(remark);
        aftersaleOperLogMapper.insert(log);
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

    private static boolean isWarehouseOrFinance(Operator operator) {
        if (operator.getRole() == null) {
            return false;
        }
        return RoleEnum.WAREHOUSE.name().equalsIgnoreCase(operator.getRole())
                || RoleEnum.FINANCE.name().equalsIgnoreCase(operator.getRole());
    }

    private AftersaleVO toVO(AftersaleOrder row, boolean withLogs) {
        AftersaleVO vo = new AftersaleVO();
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

    private AftersaleLogVO toLogVO(AftersaleOperLog log) {
        AftersaleLogVO vo = new AftersaleLogVO();
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
