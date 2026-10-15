package com.taskwhisper.common;

/**
 * 业务错误码约定（与设计文档 §5 一致）。
 * HTTP 状态码与响应体 code 相同，语义固定，全项目唯一出处。
 */
public enum ErrorCode {

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源已存在"),
    AI_UPSTREAM_FAILED(502, "AI 上游解析失败"),
    AI_NOT_CONFIGURED(503, "未配置 DeepSeek API Key，自然语言建任务不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
