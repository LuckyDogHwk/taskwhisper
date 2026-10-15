package com.taskwhisper.ai;

import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NlTaskParser 纯单元测试：不依赖 Spring，直接 new ObjectMapper。
 * 覆盖 LLM 输出的全部契约校验分支（设计文档 §6「先校验再落库」）。
 */
class NlTaskParserTest {

    private NlTaskParser parser;

    @BeforeEach
    void setUp() {
        parser = new NlTaskParser(new ObjectMapper());
    }

    /** 断言解析失败：BizException(AI_UPSTREAM_FAILED) 且 message 精确匹配 */
    private void assertParseFailure(String llmContent, String message) {
        assertThatThrownBy(() -> parser.parse(llmContent))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.errorCode()).isEqualTo(ErrorCode.AI_UPSTREAM_FAILED);
                    assertThat(biz.getMessage()).isEqualTo(message);
                });
    }

    @Test
    void parsesFullJson() {
        String json = """
                {"title":"项目周会","description":"讨论下周迭代计划","priority":"高",
                 "due_time":"2026-10-20 15:00:00","category":"工作"}
                """;
        NlTaskParser.ParsedTask task = parser.parse(json);
        assertThat(task.title()).isEqualTo("项目周会");
        assertThat(task.description()).isEqualTo("讨论下周迭代计划");
        assertThat(task.priority()).isEqualTo("高");
        assertThat(task.dueTime()).isEqualTo(LocalDateTime.of(2026, 10, 20, 15, 0, 0));
        assertThat(task.category()).isEqualTo("工作");
    }

    @Test
    void parsesIsoDueTime() {
        NlTaskParser.ParsedTask task = parser.parse("{\"title\":\"开会\",\"due_time\":\"2026-10-20T15:00:00\"}");
        assertThat(task.dueTime()).isEqualTo(LocalDateTime.of(2026, 10, 20, 15, 0, 0));
    }

    @Test
    void parsesNullableFieldsToDefaultsOrNull() {
        NlTaskParser.ParsedTask task = parser.parse(
                "{\"title\":\"跑步\",\"description\":null,\"priority\":null,\"due_time\":null,\"category\":null}");
        assertThat(task.description()).isNull();
        assertThat(task.priority()).isEqualTo("中");
        assertThat(task.dueTime()).isNull();
        assertThat(task.category()).isNull();
    }

    @Test
    void rejectsMissingTitle() {
        assertParseFailure("{\"description\":\"只有描述\"}", "LLM 输出缺少 title");
    }

    @Test
    void rejectsBlankTitle() {
        assertParseFailure("{\"title\":\"   \"}", "LLM 输出缺少 title");
    }

    @Test
    void rejectsOverlongTitle() {
        assertParseFailure("{\"title\":\"" + "a".repeat(101) + "\"}", "LLM 输出的 title 超长");
    }

    @Test
    void acceptsTitleAtMaxLength() {
        String title = "a".repeat(100);
        NlTaskParser.ParsedTask task = parser.parse("{\"title\":\"" + title + "\"}");
        assertThat(task.title()).isEqualTo(title);
    }

    @Test
    void rejectsIllegalPriority() {
        assertParseFailure("{\"title\":\"x\",\"priority\":\"紧急\"}", "LLM 输出的 priority 非法");
    }

    @Test
    void rejectsUnparsableDueTime() {
        assertParseFailure("{\"title\":\"x\",\"due_time\":\"2026-13-99 99:99\"}", "LLM 输出的 due_time 无法解析");
    }

    @Test
    void truncatesOverlongDescription() {
        NlTaskParser.ParsedTask task = parser.parse("{\"title\":\"x\",\"description\":\"" + "d".repeat(501) + "\"}");
        assertThat(task.description()).hasSize(500);
    }

    @Test
    void truncatesOverlongCategory() {
        NlTaskParser.ParsedTask task = parser.parse("{\"title\":\"x\",\"category\":\"" + "c".repeat(33) + "\"}");
        assertThat(task.category()).hasSize(32);
    }

    @Test
    void rejectsNonJsonContent() {
        assertParseFailure("帮我明天下午三点开会", "LLM 返回了非 JSON 内容");
    }

    @Test
    void rejectsNonObjectRoot() {
        assertParseFailure("[1,2,3]", "LLM 返回了非 JSON 内容");
    }

    @Test
    void defaultsEmptyPriorityToMedium() {
        assertThat(parser.parse("{\"title\":\"x\",\"priority\":\"\"}").priority()).isEqualTo("中");
        assertThat(parser.parse("{\"title\":\"x\"}").priority()).isEqualTo("中");
    }
}
