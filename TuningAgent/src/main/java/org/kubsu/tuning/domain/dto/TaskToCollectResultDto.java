package org.kubsu.tuning.domain.dto;

import org.kubsu.tuning.domain.TaskResult;

public class TaskToCollectResultDto {
    private Long taskId;
    private TaskResult taskResult;

    public TaskToCollectResultDto() {}

    public TaskToCollectResultDto(Long taskId, TaskResult taskResult) {
        this.taskId = taskId;
        this.taskResult = taskResult;
    }

    public Long getTaskId() {
        return taskId;
    }

    public TaskResult getTaskResult() {
        return taskResult;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setTaskToCollectResult(TaskResult taskResult) {
        this.taskResult = taskResult;
    }
}
