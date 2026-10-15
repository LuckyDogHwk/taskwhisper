package com.taskwhisper.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常 → 统一 Result 响应。HTTP 状态码与响应体 code 一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(e.errorCode().code())
                .body(Result.error(e.errorCode().code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.BAD_REQUEST.code(), message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.BAD_REQUEST.code(), e.getMessage()));
    }

    /**
     * 请求体不可读（JSON 语法错误 / 非 UTF-8 编码）是客户端错误，回 400 而非 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.BAD_REQUEST.code(), "请求体不是合法的 UTF-8 JSON"));
    }

    /**
     * 无对应处理器/静态资源（如 GET /）时 Spring MVC 抛出的 404 信号，
     * 必须转成 404 而不是落入兜底 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.code())
                .body(Result.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        // 服务端必须留痕：对外不泄露细节，对内保留完整堆栈
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError()
                .body(Result.error(500, "服务器内部错误"));
    }
}
