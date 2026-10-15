package com.taskwhisper.dto;

/**
 * 任务过滤分页请求。
 * status/categoryId/priority 为 null 表示不过滤；page/size 合法性由服务层校验（1 ≤ page，1 ≤ size ≤ 100）。
 */
public record TaskQueryRequest(
        String status,
        Long categoryId,
        String priority,
        long page,
        long size) {
}
