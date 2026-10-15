package com.taskwhisper.controller;

import com.taskwhisper.common.CurrentUser;
import com.taskwhisper.common.Result;
import com.taskwhisper.dto.StatsResponse;
import com.taskwhisper.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务统计：状态计数 + 分类分布（手写 SQL 实现，见 TaskMapper.xml）。
 */
@RestController
@RequestMapping("/api/tasks")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public Result<StatsResponse> stats() {
        return Result.ok(statsService.stats(CurrentUser.id()));
    }
}
