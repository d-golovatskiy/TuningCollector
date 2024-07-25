package com.example.tuningagent.controllers;

import com.example.tuningagent.entities.TaskToCollect;
import com.example.tuningagent.services.TaskToCollectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("")
    public ResponseEntity<HttpStatus> actionTaskToCollect(@RequestBody TaskToCollect taskToCollect){
        return taskToCollectService.actionTaskToCollect(taskToCollect);
    }

    @PostMapping("/prometheus")
        public String test(@RequestBody TaskToCollect taskToCollect) throws IOException {
            return taskToCollectService.queryPrometheusByTaskToCollect(taskToCollect);
        }


}
