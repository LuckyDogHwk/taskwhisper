package com.taskwhisper.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 更新任务请求（部分更新）：服务层只覆盖非 null 字段。
 * title 不要求非空：PUT 只带 status 也能通过校验（内联改状态等场景），
 * 标题合法性由非 null 时的 @Size 保证。
 */
public record TaskUpdateRequest(
        @Size(max = 100, message = "标题最长 100 字") String title,
        @Size(max = 500, message = "描述最长 500 字") String description,
        Long categoryId,
        @Pattern(regexp = "高|中|低", message = "优先级只能是 高/中/低") String priority,
        @Pattern(regexp = "待办|进行中|已完成", message = "状态只能是 待办/进行中/已完成") String status,
        LocalDateTime dueTime) {
}
