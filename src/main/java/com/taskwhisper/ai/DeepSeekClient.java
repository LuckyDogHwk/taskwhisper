package com.taskwhisper.ai;

import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import com.taskwhisper.config.AiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 客户端：chat/completions 同步调用。
 * 要点（设计文档 §6）：RestClient（Spring 6.1+ 现代 HTTP 客户端）、超时 30s、
 * temperature=0（解析场景要确定性）、response_format=json_object（强制 JSON 输出）。
 * HTTP 非 2xx 或网络异常统一转 {@link BizException}(AI_UPSTREAM_FAILED)。
 */
@Component
public class DeepSeekClient {

    /** 连接/读取超时 30s */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        // 注意：Boot 4 已移除 RestClient.Builder 的自动配置 bean，不能从容器注入。
        // 静态 builder 在 build() 时自动注册默认消息转换器（含 Jackson 3），
        // 只需替换请求工厂即可设置 30s 超时。
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 调用 POST {baseUrl}/chat/completions，返回模型回复的原始内容字符串
     * （即 LLM 输出的 JSON 文本，由 {@link NlTaskParser} 校验）。
     */
    public String chatJson(String systemPrompt, String userText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiProperties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userText)));
        body.put("temperature", 0.0);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("stream", false);

        String raw;
        try {
            raw = restClient.post()
                    .uri(aiProperties.baseUrl() + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED,
                    "DeepSeek 调用失败: HTTP " + e.getStatusCode().value() + " - " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "DeepSeek 调用失败: " + e.getMessage());
        }
        return extractContent(raw);
    }

    /** 解析原始响应：取 choices[0].message.content 即 LLM 输出文本 */
    private String extractContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "DeepSeek 调用失败: 响应为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (JacksonException e) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "DeepSeek 调用失败: 响应不是合法 JSON");
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "DeepSeek 调用失败: 响应缺少 choices[0].message.content");
        }
        return content.asString();
    }
}
