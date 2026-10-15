package com.taskwhisper.service;

import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import com.taskwhisper.common.PageResponse;
import com.taskwhisper.dto.TaskCreateRequest;
import com.taskwhisper.dto.TaskQueryRequest;
import com.taskwhisper.dto.TaskResponse;
import com.taskwhisper.dto.TaskUpdateRequest;
import com.taskwhisper.entity.Category;
import com.taskwhisper.entity.Task;
import com.taskwhisper.entity.User;
import com.taskwhisper.mapper.CategoryMapper;
import com.taskwhisper.mapper.TaskMapper;
import com.taskwhisper.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaskService 集成测试：真实 H2(MODE=MySQL)，直接注入 Service 与 Mapper 造数。
 * 覆盖：默认枚举、全字段回读、过滤组合、分页、越权、部分更新、分类归属、非法枚举。
 */
@SpringBootTest
@ActiveProfiles("test")
class TaskServiceTest {

    @Autowired
    private TaskService taskService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private TaskMapper taskMapper;

    private Long newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("x");
        userMapper.insert(user);
        return user.getId();
    }

    private Long newCategory(Long userId, String name) {
        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        categoryMapper.insert(category);
        return category.getId();
    }

    private TaskCreateRequest createReq(String title) {
        return new TaskCreateRequest(title, null, null, null, null, null);
    }

    @Test
    void createDefaultsPriorityAndStatus() {
        Long userId = newUser("task_svc_default");

        TaskResponse resp = taskService.create(userId, createReq("默认枚举任务"));

        assertThat(resp.id()).isNotNull();
        assertThat(resp.title()).isEqualTo("默认枚举任务");
        assertThat(resp.priority()).isEqualTo("中");
        assertThat(resp.status()).isEqualTo("待办");
        assertThat(resp.categoryId()).isNull();
        assertThat(resp.categoryName()).isNull();
        assertThat(resp.description()).isNull();
        assertThat(resp.dueTime()).isNull();
        assertThat(resp.createdAt()).isNotNull();
        assertThat(resp.updatedAt()).isNotNull();
    }

    @Test
    void createWithAllFieldsAndReadBack() {
        Long userId = newUser("task_svc_full");
        Long categoryId = newCategory(userId, "工作");
        LocalDateTime due = LocalDateTime.of(2026, 12, 31, 18, 30);

        TaskResponse created = taskService.create(userId,
                new TaskCreateRequest("写周报", "总结本周进展", categoryId, "高", "进行中", due));

        TaskResponse got = taskService.get(userId, created.id());
        assertThat(got.title()).isEqualTo("写周报");
        assertThat(got.description()).isEqualTo("总结本周进展");
        assertThat(got.categoryId()).isEqualTo(categoryId);
        assertThat(got.categoryName()).isEqualTo("工作");
        assertThat(got.priority()).isEqualTo("高");
        assertThat(got.status()).isEqualTo("进行中");
        assertThat(got.dueTime()).isEqualTo(due);
        assertThat(got.createdAt()).isNotNull();
        assertThat(got.updatedAt()).isNotNull();
    }

    @Test
    void pageFiltersByStatusAndPriority() {
        Long userId = newUser("task_svc_filter");
        taskService.create(userId, new TaskCreateRequest("A-待办高", null, null, "高", "待办", null));
        taskService.create(userId, new TaskCreateRequest("B-待办中", null, null, "中", "待办", null));
        taskService.create(userId, new TaskCreateRequest("C-完成高", null, null, "高", "已完成", null));

        PageResponse<TaskResponse> page = taskService.page(userId,
                new TaskQueryRequest("待办", null, "高", 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).title()).isEqualTo("A-待办高");
    }

    @Test
    void pageReturnsTotalAndPageSizeOrderedByIdDesc() {
        Long userId = newUser("task_svc_page");
        for (int i = 1; i <= 12; i++) {
            taskService.create(userId, createReq("分页任务" + i));
        }

        PageResponse<TaskResponse> first = taskService.page(userId,
                new TaskQueryRequest(null, null, null, 1, 5));
        assertThat(first.total()).isEqualTo(12);
        assertThat(first.records()).hasSize(5);
        // id 倒序：最新创建的在最前
        assertThat(first.records().get(0).title()).isEqualTo("分页任务12");

        PageResponse<TaskResponse> third = taskService.page(userId,
                new TaskQueryRequest(null, null, null, 3, 5));
        assertThat(third.total()).isEqualTo(12);
        assertThat(third.records()).hasSize(2);
        assertThat(third.records().get(0).title()).isEqualTo("分页任务2");
        assertThat(third.records().get(1).title()).isEqualTo("分页任务1");
    }

    @Test
    void pageRejectsPageZero() {
        Long userId = newUser("task_svc_page_zero");

        assertThatThrownBy(() -> taskService.page(userId,
                new TaskQueryRequest(null, null, null, 0, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageRejectsSizeOver100() {
        Long userId = newUser("task_svc_page_size");

        assertThatThrownBy(() -> taskService.page(userId,
                new TaskQueryRequest(null, null, null, 1, 101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageWithOthersCategoryReturnsEmptyPage() {
        Long userId = newUser("task_svc_page_cat");
        Long otherId = newUser("task_svc_page_cat_other");
        Long otherCategoryId = newCategory(otherId, "别人的分类");
        taskService.create(userId, createReq("我的任务"));

        PageResponse<TaskResponse> page = taskService.page(userId,
                new TaskQueryRequest(null, otherCategoryId, null, 1, 10));

        assertThat(page.total()).isEqualTo(0);
        assertThat(page.records()).isEmpty();
    }

    @Test
    void pageFillsCategoryName() {
        Long userId = newUser("task_svc_page_name");
        Long categoryId = newCategory(userId, "学习");
        taskService.create(userId, new TaskCreateRequest("背单词", null, categoryId, null, null, null));

        PageResponse<TaskResponse> page = taskService.page(userId,
                new TaskQueryRequest(null, categoryId, null, 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).categoryName()).isEqualTo("学习");
    }

    @Test
    void foreignUserCannotGetUpdateOrDelete() {
        Long ownerId = newUser("task_svc_owner");
        Long otherId = newUser("task_svc_other");
        TaskResponse created = taskService.create(ownerId, createReq("私有任务"));

        assertThatThrownBy(() -> taskService.get(otherId, created.id()))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("资源不存在");
        assertThatThrownBy(() -> taskService.update(otherId, created.id(),
                new TaskUpdateRequest("篡改", null, null, null, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode().code()).isEqualTo(404));
        assertThatThrownBy(() -> taskService.delete(otherId, created.id()))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        // 不存在的 id 同样 404
        assertThatThrownBy(() -> taskService.get(ownerId, 9_999_999L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void updateOnlyOverwritesProvidedFields() {
        Long userId = newUser("task_svc_update");
        Long categoryId = newCategory(userId, "工作");
        LocalDateTime due = LocalDateTime.of(2026, 11, 1, 9, 0);
        TaskResponse created = taskService.create(userId,
                new TaskCreateRequest("原标题", "原描述", categoryId, "低", "待办", due));

        TaskResponse updated = taskService.update(userId, created.id(),
                new TaskUpdateRequest("新标题", null, null, "高", "已完成", null));

        assertThat(updated.title()).isEqualTo("新标题");
        assertThat(updated.priority()).isEqualTo("高");
        assertThat(updated.status()).isEqualTo("已完成");
        // 未传字段保持不变
        assertThat(updated.description()).isEqualTo("原描述");
        assertThat(updated.categoryId()).isEqualTo(categoryId);
        assertThat(updated.dueTime()).isEqualTo(due);

        // 落库回读确认
        Task raw = taskMapper.selectById(created.id());
        assertThat(raw.getTitle()).isEqualTo("新标题");
        assertThat(raw.getDescription()).isEqualTo("原描述");
        assertThat(raw.getDueTime()).isEqualTo(due);
    }

    @Test
    void createRejectsOthersCategory() {
        Long userId = newUser("task_svc_cat_owner");
        Long otherId = newUser("task_svc_cat_other");
        Long otherCategoryId = newCategory(otherId, "别人的分类");

        assertThatThrownBy(() -> taskService.create(userId,
                new TaskCreateRequest("越权分类", null, otherCategoryId, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分类不存在");
        // 不存在的分类 id 同样拒绝
        assertThatThrownBy(() -> taskService.create(userId,
                new TaskCreateRequest("幽灵分类", null, 9_999_999L, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分类不存在");

        // update 时同样校验分类归属
        Long ownTaskId = taskService.create(userId, createReq("自己的任务")).id();
        assertThatThrownBy(() -> taskService.update(userId, ownTaskId,
                new TaskUpdateRequest(null, null, otherCategoryId, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分类不存在");
    }

    @Test
    void invalidEnumThrowsIllegalArgument() {
        Long userId = newUser("task_svc_enum");

        assertThatThrownBy(() -> taskService.create(userId,
                new TaskCreateRequest("非法优先级", null, null, "紧急", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("优先级");
        assertThatThrownBy(() -> taskService.create(userId,
                new TaskCreateRequest("非法状态", null, null, null, "已完成一半", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("状态");

        Long taskId = taskService.create(userId, createReq("正常任务")).id();
        assertThatThrownBy(() -> taskService.update(userId, taskId,
                new TaskUpdateRequest(null, null, null, "超高", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("优先级");
    }
}
