package com.taskwhisper.controller;

import com.taskwhisper.common.CurrentUser;
import com.taskwhisper.common.PageResponse;
import com.taskwhisper.common.Result;
import com.taskwhisper.dto.TaskCreateRequest;
import com.taskwhisper.dto.TaskQueryRequest;
import com.taskwhisper.dto.TaskResponse;
import com.taskwhisper.dto.TaskUpdateRequest;
import com.taskwhisper.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务 CRUD 与过滤分页。所有操作强制当前用户作用域（防横向越权）。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Result<PageResponse<TaskResponse>> page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        TaskQueryRequest query = new TaskQueryRequest(status, categoryId, priority, page, size);
        return Result.ok(taskService.page(CurrentUser.id(), query));
    }

    @PostMapping
    public Result<TaskResponse> create(@Valid @RequestBody TaskCreateRequest req) {
        return Result.ok(taskService.create(CurrentUser.id(), req));
    }

    @GetMapping("/{id}")
    public Result<TaskResponse> get(@PathVariable Long id) {
        return Result.ok(taskService.get(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public Result<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest req) {
        return Result.ok(taskService.update(CurrentUser.id(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(CurrentUser.id(), id);
        return Result.ok();
    }
}
