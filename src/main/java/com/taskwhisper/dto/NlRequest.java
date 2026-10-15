package com.taskwhisper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自然语言建任务请求：「明天下午三点提醒我开会，高优先级」。
 */
public record NlRequest(
        @NotBlank(message = "不能为空") @Size(max = 500, message = "最长 500 字") String text) {
}
