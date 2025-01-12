package org.kubsu.tuning.controllers;

import org.kubsu.tuning.domain.entities.TaskToCollect;
import org.kubsu.tuning.services.TaskToCollectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/v1/tasksToCollect")
public class TaskToCollectController {

    private final TaskToCollectService taskToCollectService;

    @Autowired
    public TaskToCollectController(TaskToCollectService taskToCollectService) {
        this.taskToCollectService = taskToCollectService;
    }

    @PostMapping("/prometheus")
        public String test(@RequestBody TaskToCollect taskToCollect) throws IOException {
            return taskToCollectService.queryPrometheusByTaskToCollect(taskToCollect);
        }


}
