package com.taskwhisper.common;

import java.util.List;

/**
 * 分页响应：{total, records}。
 */
public record PageResponse<T>(long total, List<T> records) {

    public static <T> PageResponse<T> of(long total, List<T> records) {
        return new PageResponse<>(total, records);
    }
}
