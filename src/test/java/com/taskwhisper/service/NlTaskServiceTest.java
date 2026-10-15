package com.taskwhisper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.taskwhisper.ai.DeepSeekClient;
import com.taskwhisper.ai.NlTaskParser;
import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import com.taskwhisper.dto.TaskResponse;
import com.taskwhisper.entity.Category;
import com.taskwhisper.entity.Task;
import com.taskwhisper.entity.User;
import com.taskwhisper.mapper.CategoryMapper;
import com.taskwhisper.mapper.TaskMapper;
import com.taskwhisper.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * NlTaskService 集成测试：H2 真实落库，仅 mock DeepSeekClient（真实 CategoryService/TaskService）。
 * 覆盖：正常解析落库、分类复用（同分类名只建一条）、无分类不建、
 * 空文本 400、LLM 输出非法 502 传播且不落库。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "taskwhisper.ai.api-key=test-key")
class NlTaskServiceTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @Autowired
    private NlTaskService nlTaskService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private CategoryMapper categoryMapper;

    @MockitoBean
    private DeepSeekClient deepSeekClient;

    private Long userId;

    @BeforeEach
    void setUpUser() {
        // t_task/t_category 都有指向 t_user 的外键，先落一个真实用户
        User user = new User();
        user.setUsername("nl_user_" + USER_SEQ.incrementAndGet());
        user.setPasswordHash("test-hash");
        userMapper.insert(user);
        userId = user.getId();
    }

    @Test
    void createFromTextParsesAndPersistsTask() {
        String llmJson = """
                {"title":"明天下午三点开会","description":"和产品对齐迭代需求",
                 "priority":"高","due_time":"2026-10-16 15:00:00","category":"工作"}
                """;
        when(deepSeekClient.chatJson(anyString(), anyString())).thenReturn(llmJson);

        TaskResponse response = nlTaskService.createFromText(userId, "明天下午三点提醒我开会，高优先级");
        assertThat(response).isNotNull();

        List<Task> tasks = tasksOf(userId);
        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getTitle()).isEqualTo("明天下午三点开会");
        assertThat(task.getDescription()).isEqualTo("和产品对齐迭代需求");
        assertThat(task.getPriority()).isEqualTo("高");
        assertThat(task.getDueTime()).isEqualTo(LocalDateTime.of(2026, 10, 16, 15, 0, 0));
        assertThat(task.getCategoryId()).isNotNull();

        // 系统提示词必须注入当前日期（模型没有「今天」的上下文，无法换算「明天」这类相对时间）
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient).chatJson(promptCaptor.capture(), eq("明天下午三点提醒我开会，高优先级"));
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains(NlTaskParser.SYSTEM_PROMPT);
        assertThat(prompt).contains(LocalDate.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd")));
    }

    @Test
    void createFromTextReusesCategoryWithSameName() {
        when(deepSeekClient.chatJson(anyString(), anyString()))
                .thenReturn("{\"title\":\"写周报\",\"category\":\"工作\"}")
                .thenReturn("{\"title\":\"改简历\",\"category\":\"工作\"}");

        nlTaskService.createFromText(userId, "周五写周报");
        nlTaskService.createFromText(userId, "周末改简历");

        // 连调两次同分类名：分类只应存在 1 条（findOrCreate 幂等复用）
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getUserId, userId));
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("工作");

        List<Task> tasks = tasksOf(userId);
        assertThat(tasks).hasSize(2);
        assertThat(tasks).allSatisfy(t -> assertThat(t.getCategoryId()).isEqualTo(categories.get(0).getId()));
    }

    @Test
    void createFromTextWithoutCategoryCreatesNoCategory() {
        when(deepSeekClient.chatJson(anyString(), anyString())).thenReturn("{\"title\":\"晚上去跑步\"}");

        TaskResponse response = nlTaskService.createFromText(userId, "晚上去跑步");
        assertThat(response).isNotNull();

        assertThat(categoryMapper.selectList(new LambdaQueryWrapper<Category>().eq(Category::getUserId, userId)))
                .isEmpty();
        List<Task> tasks = tasksOf(userId);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getCategoryId()).isNull();
    }

    @Test
    void createFromTextRejectsBlankText() {
        assertThatThrownBy(() -> nlTaskService.createFromText(userId, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入任务描述");
        assertThatThrownBy(() -> nlTaskService.createFromText(userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入任务描述");
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    void createFromTextPropagatesParserFailureWithoutPersisting() {
        when(deepSeekClient.chatJson(anyString(), anyString())).thenReturn("这不是 JSON");

        assertThatThrownBy(() -> nlTaskService.createFromText(userId, "随便一句话"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.errorCode()).isEqualTo(ErrorCode.AI_UPSTREAM_FAILED);
                    assertThat(biz.getMessage()).isEqualTo("LLM 返回了非 JSON 内容");
                });

        // 校验失败不落库
        assertThat(tasksOf(userId)).isEmpty();
        assertThat(categoryMapper.selectList(new LambdaQueryWrapper<Category>().eq(Category::getUserId, userId)))
                .isEmpty();
    }

    private List<Task> tasksOf(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getUserId, userId));
    }
}

/**
 * 未配置 DeepSeek Key 的降级路径：503 且完全不调用上游。
 * 用内联属性把 api-key 覆盖为空串；单独一个测试类保证上下文属性隔离
 * （与上面配置了 test-key 的类不能共用同一 ApplicationContext）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "taskwhisper.ai.api-key=")
class NlTaskServiceNotConfiguredTest {

    @Autowired
    private NlTaskService nlTaskService;

    @MockitoBean
    private DeepSeekClient deepSeekClient;

    @Test
    void createFromTextWithoutApiKeyThrows503() {
        assertThatThrownBy(() -> nlTaskService.createFromText(1L, "明天开会"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.errorCode()).isEqualTo(ErrorCode.AI_NOT_CONFIGURED);
                    assertThat(biz.errorCode().code()).isEqualTo(503);
                });
        verifyNoInteractions(deepSeekClient);
    }
}
