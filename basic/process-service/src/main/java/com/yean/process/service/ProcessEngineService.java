package com.yean.process.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.common.dto.process.ProcessActionDTO;
import com.yean.common.dto.process.ProcessBizStatus;
import com.yean.common.dto.process.ProcessHistoryVO;
import com.yean.common.dto.process.ProcessInstanceVO;
import com.yean.common.dto.process.ProcessLogVO;
import com.yean.common.dto.process.ProcessStartDTO;
import com.yean.common.dto.process.ProcessTaskKeys;
import com.yean.common.dto.process.ProcessTaskVO;
import com.yean.process.cmd.JumpActivityCmd;
import com.yean.process.dto.ProcessBiz;
import com.yean.process.dto.ProcessOperLog;
import com.yean.process.mapper.ProcessBizMapper;
import com.yean.process.mapper.ProcessOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.activiti.engine.ManagementService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessEngineService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ManagementService managementService;
    private final ProcessBizMapper processBizMapper;
    private final ProcessOperLogMapper processOperLogMapper;

    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO start(ProcessStartDTO dto) {
        ProcessBiz exist = findBiz(dto.getBusinessKey());
        if (exist != null) {
            throw new BizException(ErrorEnum.CONFLICT.getCode(), "流程已存在: " + dto.getBusinessKey());
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("applicantUserId", dto.getApplicantUserId());
        vars.put("bizStatus", ProcessBizStatus.RUNNING.name());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                dto.getProcessDefKey(), dto.getBusinessKey(), vars);
        Task task = currentTaskEntity(instance.getId());
        ProcessBiz biz = new ProcessBiz();
        biz.setBusinessKey(dto.getBusinessKey());
        biz.setProcessInstanceId(instance.getId());
        biz.setProcessDefKey(dto.getProcessDefKey());
        biz.setBizStatus(ProcessBizStatus.RUNNING.name());
        biz.setCurrentTaskKey(task == null ? null : task.getTaskDefinitionKey());
        biz.setApplicantUserId(dto.getApplicantUserId());
        processBizMapper.insert(biz);
        insertLog(biz, "START", null, null, task == null ? null : task.getTaskDefinitionKey(),
                dto.getOperator(), dto.getRole(), dto.getRemark());
        return toVo(biz, task, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO complete(ProcessActionDTO dto) {
        ProcessBiz biz = requireRunning(dto.getBusinessKey());
        Task task = requireCurrentTask(biz.getProcessInstanceId());
        if (StringUtils.hasText(dto.getExpectedTaskKey())
                && !dto.getExpectedTaskKey().equals(task.getTaskDefinitionKey())) {
            throw new BizException(ErrorEnum.PROCESS_TASK_MISMATCH);
        }
        assertRole(task.getTaskDefinitionKey(), dto.getRole(), dto.getUserId(), biz);
        String completedKey = task.getTaskDefinitionKey();
        taskService.claim(task.getId(), resolveAssignee(dto));
        taskService.complete(task.getId());
        Task next = currentTaskEntity(biz.getProcessInstanceId());
        boolean ended = next == null;
        if (ended) {
            biz.setBizStatus(ProcessBizStatus.DONE.name());
            biz.setCurrentTaskKey(null);
        } else {
            biz.setCurrentTaskKey(next.getTaskDefinitionKey());
        }
        processBizMapper.updateById(biz);
        insertLog(biz, "COMPLETE", completedKey, completedKey,
                next == null ? null : next.getTaskDefinitionKey(),
                dto.getOperator(), dto.getRole(), dto.getRemark());
        ProcessInstanceVO vo = toVo(biz, next, completedKey);
        vo.setEnded(ended || ProcessBizStatus.DONE.name().equals(biz.getBizStatus()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO reject(ProcessActionDTO dto) {
        ProcessBiz biz = requireRunning(dto.getBusinessKey());
        Task task = requireCurrentTask(biz.getProcessInstanceId());
        if (!ProcessTaskKeys.MERCHANT_APPROVE.equals(task.getTaskDefinitionKey())) {
            throw new BizException(ErrorEnum.PROCESS_TASK_MISMATCH.getCode(), "仅审核节点可驳回");
        }
        assertRole(task.getTaskDefinitionKey(), dto.getRole(), dto.getUserId(), biz);
        String from = task.getTaskDefinitionKey();
        runtimeService.deleteProcessInstance(biz.getProcessInstanceId(), "REJECTED");
        biz.setBizStatus(ProcessBizStatus.REJECTED.name());
        biz.setCurrentTaskKey(null);
        processBizMapper.updateById(biz);
        insertLog(biz, "REJECT", from, from, null, dto.getOperator(), dto.getRole(), dto.getRemark());
        ProcessInstanceVO vo = toVo(biz, null, from);
        vo.setEnded(true);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO timeoutClose(ProcessActionDTO dto) {
        if (!RoleEnum.ADMIN.name().equalsIgnoreCase(dto.getRole())) {
            throw new BizException(ErrorEnum.PROCESS_ROLE_DENIED);
        }
        ProcessBiz biz = requireRunning(dto.getBusinessKey());
        Task task = requireCurrentTask(biz.getProcessInstanceId());
        String from = task.getTaskDefinitionKey();
        runtimeService.deleteProcessInstance(biz.getProcessInstanceId(), "TIMEOUT_REJECTED");
        biz.setBizStatus(ProcessBizStatus.REJECTED.name());
        biz.setCurrentTaskKey(null);
        processBizMapper.updateById(biz);
        insertLog(biz, "TIMEOUT_REJECT", from, from, null, dto.getOperator(), dto.getRole(), dto.getRemark());
        ProcessInstanceVO vo = toVo(biz, null, from);
        vo.setEnded(true);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO withdraw(ProcessActionDTO dto) {
        ProcessBiz biz = requireRunning(dto.getBusinessKey());
        Task task = requireCurrentTask(biz.getProcessInstanceId());
        // 用户撤销：审核中或待仓库发货（发货前）可取消流程实例
        String taskKey = task.getTaskDefinitionKey();
        if (!ProcessTaskKeys.MERCHANT_APPROVE.equals(taskKey)
                && !ProcessTaskKeys.WAREHOUSE_SHIP.equals(taskKey)) {
            throw new BizException(ErrorEnum.PROCESS_TASK_MISMATCH.getCode(),
                    "仅待审核或待仓库发货可撤销");
        }
        boolean admin = RoleEnum.ADMIN.name().equalsIgnoreCase(dto.getRole());
        boolean applicant = dto.getUserId() != null && dto.getUserId().equals(biz.getApplicantUserId());
        if (!admin && !applicant) {
            throw new BizException(ErrorEnum.PROCESS_ROLE_DENIED);
        }
        String from = taskKey;
        runtimeService.deleteProcessInstance(biz.getProcessInstanceId(), "CANCELED");
        biz.setBizStatus(ProcessBizStatus.CANCELED.name());
        biz.setCurrentTaskKey(null);
        processBizMapper.updateById(biz);
        insertLog(biz, "WITHDRAW", from, from, null, dto.getOperator(), dto.getRole(), dto.getRemark());
        ProcessInstanceVO vo = toVo(biz, null, from);
        vo.setEnded(true);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO rollback(ProcessActionDTO dto) {
        ProcessBiz biz = requireRunning(dto.getBusinessKey());
        Task task = requireCurrentTask(biz.getProcessInstanceId());
        String previous = ProcessTaskKeys.previousTask(biz.getProcessDefKey(), task.getTaskDefinitionKey());
        if (previous == null) {
            throw new BizException(ErrorEnum.PROCESS_ROLLBACK_DENIED);
        }
        // 回退需 ADMIN 或上一节点角色 / 当前节点角色
        if (!RoleEnum.ADMIN.name().equalsIgnoreCase(dto.getRole())) {
            String currentGroup = ProcessTaskKeys.candidateGroup(task.getTaskDefinitionKey());
            if (dto.getRole() == null || !dto.getRole().equalsIgnoreCase(currentGroup)) {
                throw new BizException(ErrorEnum.PROCESS_ROLE_DENIED);
            }
        }
        String from = task.getTaskDefinitionKey();
        managementService.executeCommand(new JumpActivityCmd(biz.getProcessInstanceId(), previous));
        Task next = currentTaskEntity(biz.getProcessInstanceId());
        biz.setCurrentTaskKey(next == null ? previous : next.getTaskDefinitionKey());
        processBizMapper.updateById(biz);
        insertLog(biz, "ROLLBACK", from, from, biz.getCurrentTaskKey(),
                dto.getOperator(), dto.getRole(), dto.getRemark());
        return toVo(biz, next, null);
    }

    public ProcessInstanceVO current(String businessKey) {
        ProcessBiz biz = requireBiz(businessKey);
        Task task = null;
        if (ProcessBizStatus.RUNNING.name().equals(biz.getBizStatus())
                && StringUtils.hasText(biz.getProcessInstanceId())) {
            task = currentTaskEntity(biz.getProcessInstanceId());
        }
        ProcessInstanceVO vo = toVo(biz, task, null);
        vo.setEnded(ProcessBizStatus.valueOf(biz.getBizStatus()).isTerminal());
        return vo;
    }

    public ProcessHistoryVO history(String businessKey) {
        ProcessBiz biz = requireBiz(businessKey);
        ProcessHistoryVO vo = new ProcessHistoryVO();
        vo.setBusinessKey(biz.getBusinessKey());
        vo.setProcessInstanceId(biz.getProcessInstanceId());
        vo.setProcessDefKey(biz.getProcessDefKey());
        vo.setBizStatus(biz.getBizStatus());
        List<ProcessOperLog> logs = processOperLogMapper.selectList(
                new LambdaQueryWrapper<ProcessOperLog>()
                        .eq(ProcessOperLog::getBusinessKey, businessKey)
                        .orderByAsc(ProcessOperLog::getId));
        vo.setLogs(logs.stream().map(this::toLogVo).collect(Collectors.toList()));
        return vo;
    }

    private ProcessBiz requireRunning(String businessKey) {
        ProcessBiz biz = requireBiz(businessKey);
        ProcessBizStatus status = ProcessBizStatus.valueOf(biz.getBizStatus());
        if (status.isTerminal()) {
            throw new BizException(ErrorEnum.PROCESS_ENDED);
        }
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(biz.getProcessInstanceId())
                .singleResult();
        if (pi == null) {
            throw new BizException(ErrorEnum.PROCESS_ENDED);
        }
        return biz;
    }

    private ProcessBiz requireBiz(String businessKey) {
        ProcessBiz biz = findBiz(businessKey);
        if (biz == null) {
            throw new BizException(ErrorEnum.PROCESS_NOT_FOUND);
        }
        return biz;
    }

    private ProcessBiz findBiz(String businessKey) {
        return processBizMapper.selectOne(new LambdaQueryWrapper<ProcessBiz>()
                .eq(ProcessBiz::getBusinessKey, businessKey)
                .last("LIMIT 1"));
    }

    private Task requireCurrentTask(String processInstanceId) {
        Task task = currentTaskEntity(processInstanceId);
        if (task == null) {
            throw new BizException(ErrorEnum.PROCESS_TASK_MISMATCH.getCode(), "无当前待办任务");
        }
        return task;
    }

    private Task currentTaskEntity(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return null;
        }
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    private void assertRole(String taskKey, String role, Long userId, ProcessBiz biz) {
        if (RoleEnum.ADMIN.name().equalsIgnoreCase(role)) {
            return;
        }
        String group = ProcessTaskKeys.candidateGroup(taskKey);
        if (group == null || role == null || !group.equalsIgnoreCase(role)) {
            throw new BizException(ErrorEnum.PROCESS_ROLE_DENIED);
        }
        // 用户收货：仅申请人
        if (ProcessTaskKeys.USER_RECEIVE.equals(taskKey)
                && (userId == null || !userId.equals(biz.getApplicantUserId()))) {
            throw new BizException(ErrorEnum.PROCESS_ROLE_DENIED);
        }
    }

    private static String resolveAssignee(ProcessActionDTO dto) {
        if (StringUtils.hasText(dto.getOperator())) {
            return dto.getOperator();
        }
        return dto.getUserId() == null ? "system" : String.valueOf(dto.getUserId());
    }

    private void insertLog(ProcessBiz biz, String action, String taskKey,
                           String from, String to, String operator, String role, String remark) {
        ProcessOperLog log = new ProcessOperLog();
        log.setBusinessKey(biz.getBusinessKey());
        log.setProcessInstanceId(biz.getProcessInstanceId());
        log.setAction(action);
        log.setTaskKey(taskKey);
        log.setFromTaskKey(from);
        log.setToTaskKey(to);
        log.setOperator(operator);
        log.setRole(role);
        log.setRemark(remark);
        processOperLogMapper.insert(log);
    }

    private ProcessInstanceVO toVo(ProcessBiz biz, Task task, String completedTaskKey) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setBusinessKey(biz.getBusinessKey());
        vo.setProcessInstanceId(biz.getProcessInstanceId());
        vo.setProcessDefKey(biz.getProcessDefKey());
        vo.setBizStatus(biz.getBizStatus());
        vo.setEnded(ProcessBizStatus.valueOf(biz.getBizStatus()).isTerminal());
        vo.setCompletedTaskKey(completedTaskKey);
        if (task != null) {
            ProcessTaskVO t = new ProcessTaskVO();
            t.setTaskId(task.getId());
            t.setTaskDefinitionKey(task.getTaskDefinitionKey());
            t.setTaskName(task.getName());
            t.setCandidateGroup(ProcessTaskKeys.candidateGroup(task.getTaskDefinitionKey()));
            vo.setCurrentTask(t);
        }
        return vo;
    }

    private ProcessLogVO toLogVo(ProcessOperLog log) {
        ProcessLogVO vo = new ProcessLogVO();
        vo.setAction(log.getAction());
        vo.setTaskKey(log.getTaskKey());
        vo.setFromTaskKey(log.getFromTaskKey());
        vo.setToTaskKey(log.getToTaskKey());
        vo.setOperator(log.getOperator());
        vo.setRole(log.getRole());
        vo.setRemark(log.getRemark());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
