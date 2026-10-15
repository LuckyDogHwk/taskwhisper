package com.taskwhisper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 新建任务请求。
 * priority/status 不传时由服务层默认 中/待办；dueTime 接受 ISO 字符串（如 2026-12-31T18:30:00）。
 */
public record TaskCreateRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题最长 100 字") String title,
        @Size(max = 500, message = "描述最长 500 字") String description,
        Long categoryId,
        @Pattern(regexp = "高|中|低", message = "优先级只能是 高/中/低") String priority,
        @Pattern(regexp = "待办|进行中|已完成", message = "状态只能是 待办/进行中/已完成") String status,
        LocalDateTime dueTime) {
}
