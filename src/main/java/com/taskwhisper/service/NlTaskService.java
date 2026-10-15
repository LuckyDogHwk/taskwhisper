package com.taskwhisper.service;

import com.taskwhisper.ai.DeepSeekClient;
import com.taskwhisper.ai.NlTaskParser;
import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import com.taskwhisper.config.AiProperties;
import com.taskwhisper.dto.TaskCreateRequest;
import com.taskwhisper.dto.TaskResponse;
import org.springframework.stereotype.Service;

/**
 * AI 自然语言建任务（POST /api/tasks/nl，核心卖点）。
 * 流程：文本校验 → Key 配置检查（未配 503）→ DeepSeek 解析（上游失败 502）
 * → LLM 输出校验（非法 502，不落库）→ 分类复用/新建 → TaskService 落库。
 */
@Service
public class NlTaskService {

    private final AiProperties aiProperties;
    private final DeepSeekClient deepSeekClient;
    private final NlTaskParser nlTaskParser;
    private final CategoryService categoryService;
    private final TaskService taskService;

    public NlTaskService(AiProperties aiProperties, DeepSeekClient deepSeekClient, NlTaskParser nlTaskParser,
                         CategoryService categoryService, TaskService taskService) {
        this.aiProperties = aiProperties;
        this.deepSeekClient = deepSeekClient;
        this.nlTaskParser = nlTaskParser;
        this.categoryService = categoryService;
        this.taskService = taskService;
    }

    /**
     * 一句话建任务：「明天下午三点提醒我开会，高优先级」→ 解析 → 落库。
     *
     * @param userId 当前用户（来自 JWT）
     * @param text   用户的一句话任务描述，不能为空白
     * @return 创建成功的任务
     * @throws IllegalArgumentException 文本为空 → 400
     * @throws BizException             未配置 Key → 503；LLM 输出非法 → 502
     */
    public TaskResponse createFromText(Long userId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("请输入任务描述");
        }
        if (!aiProperties.isConfigured()) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }

        String content = deepSeekClient.chatJson(NlTaskParser.SYSTEM_PROMPT, text);
        NlTaskParser.ParsedTask parsed = nlTaskParser.parse(content);

        // 分类复用/新建：返回类型契约见 CategoryService.findOrCreate（Task 7）
        var category = parsed.category() != null
                ? categoryService.findOrCreate(userId, parsed.category())
                : null;

        TaskCreateRequest request = new TaskCreateRequest(
                parsed.title(),
                parsed.description(),
                category == null ? null : category.getId(),
                parsed.priority(),
                null,
                parsed.dueTime());
        return taskService.create(userId, request);
    }
}
