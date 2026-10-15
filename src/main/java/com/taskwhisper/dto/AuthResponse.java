package com.taskwhisper.dto;

/**
 * 注册 / 登录成功响应：JWT + 用户信息。
 */
public record AuthResponse(String token, Long userId, String username) {
}
