package com.taskwhisper.common;

/**
 * 统一响应体：{code, message, data}。
 * 成功 code=0；失败 code 即 HTTP 状态码（见 {@link ErrorCode}）。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null);
    }
}
