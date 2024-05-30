package com.example.tuningagent.controllers;

import com.example.tuningagent.entities.TaskToCollect;
import com.example.tuningagent.services.TaskToCollectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tasksToCollect")
public class TaskToCollectController {

    private final TaskToCollectService taskToCollectService;

    @Autowired
    public TaskToCollectController(TaskToCollectService taskToCollectService) {
        this.taskToCollectService = taskToCollectService;
    }

    @PostMapping("")
    public ResponseEntity<HttpStatus> actionTaskToCollect(@RequestBody TaskToCollect taskToCollect){
        return taskToCollectService.actionTaskToCollect(taskToCollect);
    }

}
