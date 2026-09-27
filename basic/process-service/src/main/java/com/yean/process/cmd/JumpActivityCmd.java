package com.yean.process.cmd;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.util.ProcessDefinitionUtil;

import java.util.List;

/**
 * Activiti 6：将运行中实例跳到指定 userTask（流程回退）。
 */
public class JumpActivityCmd implements Command<Void> {

    private final String processInstanceId;
    private final String targetActivityId;

    public JumpActivityCmd(String processInstanceId, String targetActivityId) {
        this.processInstanceId = processInstanceId;
        this.targetActivityId = targetActivityId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Void execute(CommandContext commandContext) {
        ExecutionEntity processInstance = commandContext.getExecutionEntityManager()
                .findById(processInstanceId);
        if (processInstance == null) {
            throw new IllegalStateException("process instance not found: " + processInstanceId);
        }
        List<TaskEntity> tasks = commandContext.getTaskEntityManager()
                .findTasksByProcessInstanceId(processInstanceId);
        for (TaskEntity task : tasks) {
            commandContext.getTaskEntityManager().deleteTask(task, "rollback", false, false);
        }
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstance.getProcessDefinitionId());
        FlowElement element = bpmnModel.getMainProcess().getFlowElement(targetActivityId, true);
        if (!(element instanceof FlowNode)) {
            throw new IllegalStateException("activity not found: " + targetActivityId);
        }
        ExecutionEntity execution = processInstance;
        List<? extends ExecutionEntity> children = processInstance.getExecutions();
        if (children != null && !children.isEmpty()) {
            execution = children.get(0);
        }
        execution.setCurrentFlowElement(element);
        Context.getAgenda().planContinueProcessOperation(execution);
        return null;
    }
}
