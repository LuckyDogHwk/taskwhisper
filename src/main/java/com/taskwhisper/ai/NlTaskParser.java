package com.taskwhisper.ai;

import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Set;

/**
 * LLM 输出解析器：把 DeepSeek 返回的 JSON 文本解析并校验成 {@link ParsedTask}。
 * 核心工程点：LLM 输出不可信，必须先校验后落库——校验失败统一
 * {@link BizException}(AI_UPSTREAM_FAILED) 并携带具体原因（对外 502）。
 */
@Component
public class NlTaskParser {

    /**
     * 系统提示词：定义 LLM 输出的严格 JSON 契约（设计文档 §6）。
     * 明确要求只输出 JSON 对象本身，不要 markdown 代码块或任何额外文字。
     */
    public static final String SYSTEM_PROMPT = """
            你是「TaskWhisper」任务解析助手。用户会用一句话描述一个待办任务，\
            请把它解析成一个 JSON 对象。只输出 JSON 对象本身，不要输出任何额外文字、解释或代码块标记。

            字段契约（严格遵守）：
            {
              "title": string，必填，任务标题，提炼用户要做什么，不超过 100 字；
              "description": string，可空，任务详细描述，不超过 500 字；
              "priority": "高" | "中" | "低"，可空，任务优先级；
              "due_time": "yyyy-MM-dd HH:mm:ss"，可空，任务截止时间；
              "category": string，可空，任务分类名，不超过 32 字。
            }
            用户提到的信息如实填入对应字段；用户没有提到的字段一律给 null。
            """;

    /**
     * 组装实际发送的系统提示词：注入当前日期（含星期）。
     * 模型没有「今天」的上下文，不注入日期时无法把「明天/下周五/今晚八点」
     * 这类相对时间换算成绝对日期，只能放弃填 due_time（真实调用实测踩坑）。
     */
    public String systemPrompt(LocalDateTime now) {
        String today = now.format(DateTimeFormatter.ofPattern("uuuu-MM-dd EEEE", Locale.CHINA));
        return SYSTEM_PROMPT + "\n\n当前时间信息：今天是 " + today + "。"
                + "用户提到相对时间（明天、下周五、今晚八点、下周一等）时，"
                + "必须换算成绝对日期时间填入 due_time。";
    }

    /** 契约字段上限 */
    private static final int TITLE_MAX = 100;
    private static final int DESCRIPTION_MAX = 500;
    private static final int CATEGORY_MAX = 32;

    /** 合法优先级（默认「中」） */
    private static final Set<String> PRIORITIES = Set.of("高", "中", "低");

    /**
     * 契约时间格式（STRICT：拒绝 2026-13-99 99:99 这类非法日期）。
     * 注意用 uuuu 而不是 yyyy：yyyy 是纪元年，STRICT 模式下缺纪元会拒绝解析。
     */
    private static final DateTimeFormatter DUE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("uuuu-MM-dd HH:mm:ss")
                    .toFormatter(Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private final ObjectMapper objectMapper;

    public NlTaskParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析并校验 LLM 输出。
     * 校验规则：根必须是 JSON 对象；title 必填且 ≤100 字；priority 空补「中」、
     * 非空必须在 {高,中,低}；due_time 非空必须按契约格式可解析；
     * description ≤500 字、category ≤32 字（超长截断，不报错）。
     */
    public ParsedTask parse(String llmContent) {
        JsonNode root;
        try {
            root = objectMapper.readTree(llmContent);
        } catch (JacksonException e) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 返回了非 JSON 内容");
        }
        if (root == null || !root.isObject()) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 返回了非 JSON 内容");
        }

        String title = textOrNull(root.get("title"));
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 输出缺少 title");
        }
        if (title.length() > TITLE_MAX) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 输出的 title 超长");
        }

        String description = textOrNull(root.get("description"));
        if (description != null && description.length() > DESCRIPTION_MAX) {
            description = description.substring(0, DESCRIPTION_MAX);
        }

        String priority = textOrNull(root.get("priority"));
        if (priority != null && !priority.isBlank() && !PRIORITIES.contains(priority)) {
            throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 输出的 priority 非法");
        }
        if (priority == null || priority.isBlank()) {
            priority = "中";
        }

        String category = textOrNull(root.get("category"));
        if (category != null && category.length() > CATEGORY_MAX) {
            category = category.substring(0, CATEGORY_MAX);
        }

        LocalDateTime dueTime = parseDueTime(root.get("due_time"));
        return new ParsedTask(title, description, priority, dueTime, category);
    }

    /** due_time 非空时必须能按契约格式解析，否则视为 LLM 输出错误（502） */
    private LocalDateTime parseDueTime(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        String text = node.asText();
        try {
            return LocalDateTime.parse(text, DUE_TIME_FORMATTER);
        } catch (DateTimeParseException e1) {
            try {
                // 兼容 ISO 格式（如 LLM 输出 2026-10-20T15:00:00）
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                throw new BizException(ErrorCode.AI_UPSTREAM_FAILED, "LLM 输出的 due_time 无法解析");
            }
        }
    }

    /** JSON 取字符串：字段缺失或 null 一律返回 null（可空字段契约） */
    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    /**
     * 解析并校验后的任务字段。
     * priority 已兜底为「中」；description/category 已按契约长度截断。
     */
    public record ParsedTask(String title, String description, String priority, LocalDateTime dueTime, String category) {
    }
}
