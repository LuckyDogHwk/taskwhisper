package com.taskwhisper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求：用户名 3-32 位，密码 6-64 位。
 */
public record RegisterRequest(
        @NotBlank(message = "不能为空") @Size(min = 3, max = 32, message = "长度需在 3-32 之间") String username,
        @NotBlank(message = "不能为空") @Size(min = 6, max = 64, message = "长度需在 6-64 之间") String password) {
}
