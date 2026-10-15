package com.taskwhisper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 配置（application.yml 中 taskwhisper.ai.*）。
 * api-key 来自环境变量 DEEPSEEK_API_KEY，不配置时为空——自然语言建任务
 * 返回 503 诚实降级，禁止本地规则兜底（设计文档 §6）。
 */
@Component
public class AiProperties {

    @Value("${taskwhisper.ai.api-key}")
    private String apiKey;

    @Value("${taskwhisper.ai.base-url}")
    private String baseUrl;

    @Value("${taskwhisper.ai.model}")
    private String model;

    /** DeepSeek API Key；未配置时为 null 或空白 */
    public String apiKey() {
        return apiKey;
    }

    /** DeepSeek 接口基地址，如 https://api.deepseek.com */
    public String baseUrl() {
        return baseUrl;
    }

    /** 模型名，如 deepseek-chat */
    public String model() {
        return model;
    }

    /** 是否已配置 API Key（自然语言建任务的前置条件） */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
