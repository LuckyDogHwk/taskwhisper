package com.taskwhisper.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求：仅校验非空，其余交给 AuthService 统一防枚举文案。
 */
public record LoginRequest(
        @NotBlank(message = "不能为空") String username,
        @NotBlank(message = "不能为空") String password) {
}
