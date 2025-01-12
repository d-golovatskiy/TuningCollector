package org.kubsu.tuning.domain.dto;

import org.kubsu.tuning.domain.TaskToCollectResult;

public class TaskToCollectResultDto {
    private Long taskId;
    private TaskToCollectResult taskToCollectResult;

    public TaskToCollectResultDto() {}

    public TaskToCollectResultDto(Long taskId, TaskToCollectResult taskToCollectResult) {
        this.taskId = taskId;
        this.taskToCollectResult = taskToCollectResult;
    }

    public Long getTaskId() {
        return taskId;
    }

    public TaskToCollectResult getTaskToCollectResult() {
        return taskToCollectResult;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setTaskToCollectResult(TaskToCollectResult taskToCollectResult) {
        this.taskToCollectResult = taskToCollectResult;
    }
}
