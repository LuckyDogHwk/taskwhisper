package com.taskwhisper.dto;

import java.util.List;
import java.util.Map;

/**
 * 统计响应：各状态任务计数 + 分类分布。
 *
 * @param statusCounts   状态 → 任务数，键固定为 待办/进行中/已完成（无数据的键补 0）
 * @param categoryCounts 分类分布，含未分类桶（categoryId 为 null）
 */
public record StatsResponse(Map<String, Long> statusCounts, List<CategoryCount> categoryCounts) {

    /**
     * 单个分类桶的计数。
     *
     * @param categoryId   分类 id，未分类桶为 null
     * @param categoryName 分类名，未分类桶固定为「未分类」
     * @param count        该分类下的任务数
     */
    public record CategoryCount(Long categoryId, String categoryName, long count) {
    }
}
