package com.taskwhisper.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 取当前登录用户 id。
 * principal 由 JwtAuthFilter 装载（Long userId），见 security 包。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
