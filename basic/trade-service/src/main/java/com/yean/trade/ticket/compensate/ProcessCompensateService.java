package com.yean.trade.ticket.compensate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessTaskVO;
import com.yean.common.enums.RoleEnum;
import com.yean.common.result.R;
import com.yean.common.rpc.ProcessFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 流程补偿：本地事务失败且 process 已推进时入队，立即尝试 + 定时扫表重试 rollback，
 * 直到当前节点回到 {@code expectedTaskKey}（与未改成功的本地单据对齐）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCompensateService {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAIL = "FAIL";
    public static final String LOCK_KEY = "lock:schedule:process-compensate";

    private final ProcessCompensateMapper processCompensateMapper;
    private final ProcessCompensateProperties properties;
    private final ProcessFeignClient processFeignClient;
    private final RedissonClient redissonClient;

    /**
     * 先落库再立刻试一次；即时成功则标 DONE，失败留给扫表。
     */
    public void enqueueAndTry(String aftersaleNo, String expectedTaskKey, Throwable cause) {
        if (!StringUtils.hasText(aftersaleNo) || !StringUtils.hasText(expectedTaskKey)) {
            log.error("process compensate skip enqueue, aftersaleNo={}, expected={}", aftersaleNo, expectedTaskKey);
            return;
        }
        ProcessCompensate row = enqueue(aftersaleNo, expectedTaskKey, cause);
        if (row == null) {
            return;
        }
        tryOnce(row);
    }

    public ProcessCompensate enqueue(String aftersaleNo, String expectedTaskKey, Throwable cause) {
        ProcessCompensate open = processCompensateMapper.selectOne(
                Wrappers.<ProcessCompensate>lambdaQuery()
                        .eq(ProcessCompensate::getAftersaleNo, aftersaleNo)
                        .eq(ProcessCompensate::getStatus, STATUS_NEW)
                        .last("LIMIT 1")
        );
        if (open != null) {
            open.setExpectedTaskKey(expectedTaskKey);
            open.setCause(abbrev(cause));
            open.setNextRetryTime(LocalDateTime.now());
            open.setUpdateTime(LocalDateTime.now());
            processCompensateMapper.updateById(open);
            log.warn("process compensate re-queued, id={}, aftersaleNo={}", open.getId(), aftersaleNo);
            return open;
        }
        ProcessCompensate row = new ProcessCompensate();
        row.setAftersaleNo(aftersaleNo);
        row.setExpectedTaskKey(expectedTaskKey);
        row.setCause(abbrev(cause));
        row.setStatus(STATUS_NEW);
        row.setRetryCount(0);
        row.setNextRetryTime(LocalDateTime.now());
        processCompensateMapper.insert(row);
        log.warn("process compensate enqueued, id={}, aftersaleNo={}, expected={}",
                row.getId(), aftersaleNo, expectedTaskKey);
        return row;
    }

    public int dispatchDue() {
        if (!properties.isEnabled()) {
            log.info("process-compensate dispatch disabled");
            return 0;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.info("process-compensate dispatch skipped, lock held");
                return 0;
            }
            return doDispatch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("process-compensate dispatch interrupted");
            return 0;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int doDispatch() {
        LocalDateTime now = LocalDateTime.now();
        List<ProcessCompensate> rows = processCompensateMapper.selectList(
                Wrappers.<ProcessCompensate>lambdaQuery()
                        .eq(ProcessCompensate::getStatus, STATUS_NEW)
                        .le(ProcessCompensate::getNextRetryTime, now)
                        .orderByAsc(ProcessCompensate::getId)
                        .last("LIMIT " + Math.max(1, properties.getScanLimit()))
        );
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int done = 0;
        for (ProcessCompensate row : rows) {
            if (tryOnce(row)) {
                done++;
            }
        }
        log.info("process-compensate dispatch finished, scanned={}, done={}", rows.size(), done);
        return done;
    }

    /**
     * @return true 已对齐并标 DONE
     */
    public boolean tryOnce(ProcessCompensate row) {
        if (row == null || row.getId() == null) {
            return false;
        }
        // 重新读，避免脏写
        ProcessCompensate fresh = processCompensateMapper.selectById(row.getId());
        if (fresh == null || !STATUS_NEW.equals(fresh.getStatus())) {
            return false;
        }
        try {
            if (aligned(fresh.getAftersaleNo(), fresh.getExpectedTaskKey())) {
                markDone(fresh, null);
                return true;
            }
            ProcessActionDTO compensate = new ProcessActionDTO();
            compensate.setBusinessKey(fresh.getAftersaleNo());
            compensate.setOperator("system");
            compensate.setRole(RoleEnum.ADMIN.name());
            compensate.setRemark("补偿扫表：本地失败后回滚流程至 " + fresh.getExpectedTaskKey());
            R.unwrap(processFeignClient.rollback(compensate));
            if (aligned(fresh.getAftersaleNo(), fresh.getExpectedTaskKey())) {
                markDone(fresh, null);
                log.warn("process compensate succeeded, id={}, aftersaleNo={}", fresh.getId(), fresh.getAftersaleNo());
                return true;
            }
            // 回滚成功但仍未对齐（少见）：记失败进度，继续扫
            markRetry(fresh, "rolled back but still not at " + fresh.getExpectedTaskKey());
            return false;
        } catch (Exception e) {
            markRetry(fresh, e.getMessage());
            log.error("process compensate try failed, id={}, aftersaleNo={}, err={}",
                    fresh.getId(), fresh.getAftersaleNo(), e.getMessage());
            return false;
        }
    }

    private boolean aligned(String aftersaleNo, String expectedTaskKey) {
        ProcessInstanceVO current = R.unwrap(processFeignClient.current(aftersaleNo));
        if (current == null) {
            return false;
        }
        ProcessTaskVO task = current.getCurrentTask();
        String key = task == null ? null : task.getTaskDefinitionKey();
        return expectedTaskKey != null && expectedTaskKey.equals(key);
    }

    private void markDone(ProcessCompensate row, String lastError) {
        row.setStatus(STATUS_DONE);
        row.setLastError(lastError);
        row.setUpdateTime(LocalDateTime.now());
        processCompensateMapper.updateById(row);
    }

    private void markRetry(ProcessCompensate row, String error) {
        int nextRetry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        row.setRetryCount(nextRetry);
        row.setLastError(abbrev(error));
        row.setUpdateTime(LocalDateTime.now());
        if (nextRetry >= properties.getMaxRetry()) {
            row.setStatus(STATUS_FAIL);
            log.error("process compensate give up, id={}, aftersaleNo={}", row.getId(), row.getAftersaleNo());
        } else {
            int delaySec = Math.max(1, properties.getRetryBaseSeconds()) * nextRetry;
            row.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySec));
        }
        processCompensateMapper.updateById(row);
    }

    private static String abbrev(Throwable cause) {
        if (cause == null) {
            return null;
        }
        return abbrev(cause.toString());
    }

    private static String abbrev(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }
}
