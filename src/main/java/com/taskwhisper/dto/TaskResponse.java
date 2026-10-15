package com.taskwhisper.dto;

import java.time.LocalDateTime;

/**
 * 任务响应。categoryName 由服务层按分类 id 批量映射填充（未分类时为 null）。
 */
public record TaskResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        String priority,
        String status,
        LocalDateTime dueTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
