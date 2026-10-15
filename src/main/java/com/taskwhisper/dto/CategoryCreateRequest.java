package com.taskwhisper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建分类请求。
 */
public record CategoryCreateRequest(
        @NotBlank(message = "分类名不能为空") @Size(max = 32, message = "分类名最长 32 字") String name) {
}
