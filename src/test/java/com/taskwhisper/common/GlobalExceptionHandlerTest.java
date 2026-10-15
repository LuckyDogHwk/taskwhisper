package com.taskwhisper.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void bizExceptionMapsToItsErrorCode() {
        ResponseEntity<Result<Void>> resp = handler.handleBiz(new BizException(ErrorCode.CONFLICT));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody().code()).isEqualTo(409);
        assertThat(resp.getBody().message()).isEqualTo("资源已存在");
    }

    @Test
    void bizExceptionWithDetailUsesDetailAsMessage() {
        ResponseEntity<Result<Void>> resp =
                handler.handleBiz(new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 返回非法 JSON"));
        assertThat(resp.getStatusCode().value()).isEqualTo(502);
        assertThat(resp.getBody().code()).isEqualTo(502);
        assertThat(resp.getBody().message()).isEqualTo("LLM 返回非法 JSON");
    }

    @Test
    void unknownExceptionMapsTo500WithGenericMessage() {
        ResponseEntity<Result<Void>> resp = handler.handleOther(new RuntimeException("boom"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().code()).isEqualTo(500);
        assertThat(resp.getBody().message()).isEqualTo("服务器内部错误");
    }

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<Result<Void>> resp = handler.handleIllegalArgument(new IllegalArgumentException("页码不能为负"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().code()).isEqualTo(400);
        assertThat(resp.getBody().message()).isEqualTo("页码不能为负");
    }
}
