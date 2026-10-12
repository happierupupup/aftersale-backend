package com.yean.trade.ticket.start;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yean.common.dto.Operator;
import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessStartDTO;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.common.result.R;
import com.yean.common.rpc.ProcessFeignClient;
import com.yean.trade.notify.AftersaleNotifyPublisher;
import com.yean.trade.ticket.dto.Ticket;
import com.yean.trade.ticket.dto.TicketOperLog;
import com.yean.trade.ticket.enums.TicketAction;
import com.yean.trade.ticket.enums.TicketStatus;
import com.yean.trade.ticket.mapper.TicketMapper;
import com.yean.trade.ticket.mapper.TicketOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 流程启动：apply 同事务入队，提交后异步 / 定时扫表 Feign process/start，
 * 成功回写 processInstanceId 并发申请通知；超限失败则关单释槽。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessStartService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String LOCK_KEY = "lock:schedule:process-start";

    private final ProcessStartJobMapper processStartJobMapper;
    private final ProcessStartProperties properties;
    private final ProcessFeignClient processFeignClient;
    private final TicketMapper ticketMapper;
    private final TicketOperLogMapper ticketOperLogMapper;
    private final AftersaleNotifyPublisher aftersaleNotifyPublisher;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    public void enqueue(String aftersaleNo,
                        String processDefKey,
                        Long applicantUserId,
                        String operatorName,
                        String operatorRole,
                        String remark) {
        ProcessStartJob row = new ProcessStartJob();
        row.setAftersaleNo(aftersaleNo);
        row.setProcessDefKey(processDefKey);
        row.setApplicantUserId(applicantUserId);
        row.setOperatorName(operatorName);
        row.setOperatorRole(operatorRole);
        row.setRemark(remark);
        row.setStatus(STATUS_NEW);
        row.setRetryCount(0);
        row.setNextRetryTime(LocalDateTime.now());
        try {
            processStartJobMapper.insert(row);
            log.info("process start enqueued, id={}, aftersaleNo={}", row.getId(), aftersaleNo);
        } catch (DuplicateKeyException e) {
            ProcessStartJob exist = processStartJobMapper.selectOne(
                    Wrappers.<ProcessStartJob>lambdaQuery()
                            .eq(ProcessStartJob::getAftersaleNo, aftersaleNo)
                            .last("LIMIT 1"));
            if (exist != null && STATUS_NEW.equals(exist.getStatus())) {
                exist.setProcessDefKey(processDefKey);
                exist.setApplicantUserId(applicantUserId);
                exist.setOperatorName(operatorName);
                exist.setOperatorRole(operatorRole);
                exist.setRemark(remark);
                exist.setNextRetryTime(LocalDateTime.now());
                exist.setUpdateTime(LocalDateTime.now());
                processStartJobMapper.updateById(exist);
                log.info("process start re-queued, id={}, aftersaleNo={}", exist.getId(), aftersaleNo);
            } else {
                log.info("process start job already exists, aftersaleNo={}, status={}",
                        aftersaleNo, exist == null ? null : exist.getStatus());
            }
        }
    }

    /** 取消尚未 start 的任务（用户撤回 / 超时本地关单） */
    public void cancelIfNew(String aftersaleNo, String reason) {
        ProcessStartJob job = findByAftersaleNo(aftersaleNo);
        if (job == null || !STATUS_NEW.equals(job.getStatus())) {
            return;
        }
        job.setStatus(STATUS_CANCELLED);
        job.setLastError(abbrev(reason));
        job.setUpdateTime(LocalDateTime.now());
        processStartJobMapper.updateById(job);
        log.info("process start cancelled, id={}, aftersaleNo={}", job.getId(), aftersaleNo);
    }

    public void tryOnceByAftersaleNo(String aftersaleNo) {
        ProcessStartJob job = findByAftersaleNo(aftersaleNo);
        if (job != null) {
            tryOnce(job);
        }
    }

    public int dispatchDue() {
        if (!properties.isEnabled()) {
            log.info("process-start dispatch disabled");
            return 0;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.info("process-start dispatch skipped, lock held");
                return 0;
            }
            return doDispatch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("process-start dispatch interrupted");
            return 0;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int doDispatch() {
        LocalDateTime now = LocalDateTime.now();
        List<ProcessStartJob> rows = processStartJobMapper.selectList(
                Wrappers.<ProcessStartJob>lambdaQuery()
                        .eq(ProcessStartJob::getStatus, STATUS_NEW)
                        .le(ProcessStartJob::getNextRetryTime, now)
                        .orderByAsc(ProcessStartJob::getId)
                        .last("LIMIT " + Math.max(1, properties.getScanLimit()))
        );
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int done = 0;
        for (ProcessStartJob row : rows) {
            if (tryOnce(row)) {
                done++;
            }
        }
        log.info("process-start dispatch finished, scanned={}, done={}", rows.size(), done);
        return done;
    }

    /**
     * @return true 已 DONE（含单据已有流程 / 任务已取消）
     */
    public boolean tryOnce(ProcessStartJob row) {
        if (row == null || row.getId() == null || !StringUtils.hasText(row.getAftersaleNo())) {
            return false;
        }
        // 同一售后单：提交后抢跑 + 扫表可能并发，串行化避免双启 process
        RLock lock = redissonClient.getLock("lock:process-start:biz:" + row.getAftersaleNo());
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!locked) {
                return false;
            }
            return doTryOnce(row);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean doTryOnce(ProcessStartJob row) {
        ProcessStartJob fresh = processStartJobMapper.selectById(row.getId());
        if (fresh == null || !STATUS_NEW.equals(fresh.getStatus())) {
            return false;
        }
        Ticket ticket = ticketMapper.selectByAftersaleNo(fresh.getAftersaleNo());
        if (ticket == null || TicketStatus.isTerminal(ticket.getStatus()) || TicketStatus.isDraft(ticket.getStatus())) {
            markStatus(fresh, STATUS_CANCELLED, "ticket missing or terminal");
            return true;
        }
        if (StringUtils.hasText(ticket.getProcessInstanceId())) {
            markStatus(fresh, STATUS_DONE, null);
            return true;
        }
        try {
            ProcessInstanceVO process = startOrLoad(fresh);
            if (process == null || !StringUtils.hasText(process.getProcessInstanceId())) {
                markRetry(fresh, "empty processInstanceId");
                return false;
            }
            String processInstanceId = process.getProcessInstanceId();
            Boolean bound = transactionTemplate.execute(status -> {
                Ticket latest = ticketMapper.selectByAftersaleNo(fresh.getAftersaleNo());
                if (latest == null || TicketStatus.isTerminal(latest.getStatus())) {
                    return false;
                }
                if (!StringUtils.hasText(latest.getProcessInstanceId())) {
                    latest.setProcessInstanceId(processInstanceId);
                    ticketMapper.updateById(latest);
                    aftersaleNotifyPublisher.publish(latest, TicketAction.APPLY);
                }
                return true;
            });
            if (!Boolean.TRUE.equals(bound)) {
                // 本地已撤回/关单，但 process 已起 → 关掉孤儿流程
                closeOrphanProcess(fresh.getAftersaleNo());
                markStatus(fresh, STATUS_CANCELLED, "ticket terminal after process started");
                log.warn("process start orphan closed, id={}, aftersaleNo={}, piid={}",
                        fresh.getId(), fresh.getAftersaleNo(), processInstanceId);
                return true;
            }
            markStatus(fresh, STATUS_DONE, null);
            log.info("process start succeeded, id={}, aftersaleNo={}, piid={}",
                    fresh.getId(), fresh.getAftersaleNo(), processInstanceId);
            return true;
        } catch (Exception e) {
            markRetry(fresh, e.getMessage());
            log.error("process start try failed, id={}, aftersaleNo={}, err={}",
                    fresh.getId(), fresh.getAftersaleNo(), e.getMessage());
            return false;
        }
    }

    private void closeOrphanProcess(String aftersaleNo) {
        try {
            ProcessActionDTO dto = new ProcessActionDTO();
            dto.setBusinessKey(aftersaleNo);
            dto.setOperator("system");
            dto.setRole(RoleEnum.ADMIN.name());
            dto.setRemark("本地单已终态，关闭异步启动的孤儿流程");
            R.unwrap(processFeignClient.timeoutClose(dto));
        } catch (Exception e) {
            log.error("close orphan process failed, aftersaleNo={}, err={}", aftersaleNo, e.getMessage());
        }
    }

    private ProcessInstanceVO startOrLoad(ProcessStartJob job) {
        ProcessStartDTO start = new ProcessStartDTO();
        start.setBusinessKey(job.getAftersaleNo());
        start.setProcessDefKey(job.getProcessDefKey());
        start.setApplicantUserId(job.getApplicantUserId());
        start.setOperator(job.getOperatorName());
        start.setRole(job.getOperatorRole());
        start.setRemark(job.getRemark());
        try {
            return R.unwrap(processFeignClient.start(start));
        } catch (BizException e) {
            // 与原先一致：流程已存在 / 并发落库冲突 → 回查当前投影回填
            if (e.getCode() == ErrorEnum.CONFLICT.getCode()) {
                return R.unwrap(processFeignClient.current(job.getAftersaleNo()));
            }
            throw e;
        }
    }

    private void markRetry(ProcessStartJob row, String error) {
        int nextRetry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        row.setRetryCount(nextRetry);
        row.setLastError(abbrev(error));
        row.setUpdateTime(LocalDateTime.now());
        if (nextRetry >= properties.getMaxRetry()) {
            row.setStatus(STATUS_FAIL);
            processStartJobMapper.updateById(row);
            failTicket(row.getAftersaleNo(), "流程启动失败已超限重试，自动关单");
            log.error("process start give up, id={}, aftersaleNo={}", row.getId(), row.getAftersaleNo());
        } else {
            int delaySec = Math.max(1, properties.getRetryBaseSeconds()) * nextRetry;
            row.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySec));
            processStartJobMapper.updateById(row);
        }
    }

    private void failTicket(String aftersaleNo, String remark) {
        Ticket ticket = ticketMapper.selectByAftersaleNo(aftersaleNo);
        if (ticket == null || TicketStatus.isTerminal(ticket.getStatus())) {
            return;
        }
        int before = ticket.getStatus() == null ? -1 : ticket.getStatus();
        transactionTemplate.execute(status -> {
            ticketMapper.updateStatus(ticket.getId(), TicketStatus.CANCELED.getCode(), true, null, null);
            stringRedisTemplate.delete("aftersale:idem:" + ticket.getOrderNo() + ":" + ticket.getSkuId());
            Operator system = new Operator(0L, "SYSTEM", "ADMIN");
            TicketOperLog logRow = new TicketOperLog();
            logRow.setAftersaleNo(aftersaleNo);
            logRow.setOperatorId(system.getUserId());
            logRow.setOperatorName(system.getUsername());
            logRow.setAction(TicketAction.WITHDRAW.name());
            logRow.setBeforeStatus(before);
            logRow.setAfterStatus(TicketStatus.CANCELED.getCode());
            logRow.setRemark(remark);
            ticketOperLogMapper.insert(logRow);
            return null;
        });
    }

    private void markStatus(ProcessStartJob row, String status, String lastError) {
        row.setStatus(status);
        row.setLastError(abbrev(lastError));
        row.setUpdateTime(LocalDateTime.now());
        processStartJobMapper.updateById(row);
    }

    private ProcessStartJob findByAftersaleNo(String aftersaleNo) {
        return processStartJobMapper.selectOne(
                Wrappers.<ProcessStartJob>lambdaQuery()
                        .eq(ProcessStartJob::getAftersaleNo, aftersaleNo)
                        .last("LIMIT 1"));
    }

    private static String abbrev(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }
}
