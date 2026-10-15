package com.taskwhisper.controller;

import com.taskwhisper.common.CurrentUser;
import com.taskwhisper.common.Result;
import com.taskwhisper.dto.NlRequest;
import com.taskwhisper.dto.TaskResponse;
import com.taskwhisper.service.NlTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 自然语言建任务（核心卖点）。
 * 未配置 DeepSeek Key 时返回 503；LLM 输出校验失败返回 502，不落库。
 */
@RestController
@RequestMapping("/api/tasks")
public class NlController {

    private final NlTaskService nlTaskService;

    public NlController(NlTaskService nlTaskService) {
        this.nlTaskService = nlTaskService;
    }

    @PostMapping("/nl")
    public Result<TaskResponse> createFromNl(@Valid @RequestBody NlRequest req) {
        return Result.ok(nlTaskService.createFromText(CurrentUser.id(), req.text()));
    }
}
