package com.taskwhisper.service;

import com.taskwhisper.dto.StatsResponse;
import com.taskwhisper.dto.StatsResponse.CategoryCount;
import com.taskwhisper.mapper.TaskMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务统计：各状态任务计数 + 分类分布。
 * SQL 实现在 TaskMapper.xml（手写 XML 复杂 SQL，面试「会不会写原生 SQL」的答案）。
 */
@Service
public class StatsService {

    /** 未分类桶的固定展示名 */
    private static final String UNCATEGORIZED_NAME = "未分类";

    /** 状态全量键：SQL 查不到的状态也补 0 返回，前端可直接渲染三张卡片 */
    private static final List<String> ALL_STATUSES = List.of("待办", "进行中", "已完成");

    private final TaskMapper taskMapper;

    public StatsService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 统计指定用户的各状态任务数与分类分布。
     * 只统计该用户自己的数据：两条 SQL 均带 user_id 条件（防横向越权）。
     *
     * @param userId 用户 id
     * @return 状态计数（三个键齐全）+ 分类分布（含未分类桶）
     */
    public StatsResponse stats(Long userId) {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (String status : ALL_STATUSES) {
            statusCounts.put(status, 0L);
        }
        for (Map<String, Object> row : taskMapper.countByStatus(userId)) {
            statusCounts.put(String.valueOf(row.get("status")), toLong(row.get("cnt")));
        }

        List<CategoryCount> categoryCounts = taskMapper.countByCategory(userId).stream()
                .map(row -> new CategoryCount(
                        toNullableLong(row.get("category_id")),
                        row.get("name") == null ? UNCATEGORIZED_NAME : String.valueOf(row.get("name")),
                        toLong(row.get("cnt"))))
                .toList();

        return new StatsResponse(statusCounts, categoryCounts);
    }

    /** H2/MySQL 对 COUNT(*) 的返回类型可能是 Integer/Long/BigDecimal，统一转 long */
    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    /** category_id 为 null 时保持 null（未分类桶），否则转 Long */
    private static Long toNullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
