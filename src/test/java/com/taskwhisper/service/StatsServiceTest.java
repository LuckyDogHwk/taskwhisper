package com.taskwhisper.service;

import com.taskwhisper.dto.StatsResponse;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统计模块回归锚点（H2 MODE=MySQL 真实 SQL）：
 * 手写 XML 聚合正确性、缺数据状态补 0、未分类桶、跨用户数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
class StatsServiceTest {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private StatsService statsService;

    @Test
    void statsCountsStatusesAndCategoryBucketsForCurrentUserOnly() {
        // 用户 A：待办×2、进行中×1、已完成×1；其中 工作×2、学习×1、无分类×1
        User userA = insertUser("stats_a");
        User userB = insertUser("stats_b");

        Category work = insertCategory(userA.getId(), "工作");
        Category study = insertCategory(userA.getId(), "学习");

        insertTask(userA.getId(), work.getId(), "A-待办-工作", "待办");
        insertTask(userA.getId(), study.getId(), "A-待办-学习", "待办");
        insertTask(userA.getId(), work.getId(), "A-进行中-工作", "进行中");
        insertTask(userA.getId(), null, "A-已完成-无分类", "已完成");

        // 用户 B：待办×5（污染数据，必须与 A 完全隔离）
        for (int i = 1; i <= 5; i++) {
            insertTask(userB.getId(), null, "B-待办-" + i, "待办");
        }

        // A 的统计：状态 2/1/1，键总数固定为 3（B 的 5 条一根都不计入）
        StatsResponse statsA = statsService.stats(userA.getId());

        assertThat(statsA.statusCounts()).hasSize(3);
        assertThat(statsA.statusCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of("待办", 2L, "进行中", 1L, "已完成", 1L));

        assertThat(statsA.categoryCounts()).containsExactlyInAnyOrder(
                new StatsResponse.CategoryCount(work.getId(), "工作", 2),
                new StatsResponse.CategoryCount(study.getId(), "学习", 1),
                new StatsResponse.CategoryCount(null, "未分类", 1));

        // 换 userId 调 stats：B 只能看到自己的 5 条待办，A 的分类一个都不泄漏
        StatsResponse statsB = statsService.stats(userB.getId());

        assertThat(statsB.statusCounts()).hasSize(3);
        assertThat(statsB.statusCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of("待办", 5L, "进行中", 0L, "已完成", 0L));
        assertThat(statsB.categoryCounts()).containsExactly(
                new StatsResponse.CategoryCount(null, "未分类", 5));
    }

    private User insertUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("x");
        userMapper.insert(user);
        return user;
    }

    private Category insertCategory(Long userId, String name) {
        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        categoryMapper.insert(category);
        return category;
    }

    private Task insertTask(Long userId, Long categoryId, String title, String status) {
        Task task = new Task();
        task.setUserId(userId);
        task.setCategoryId(categoryId);
        task.setTitle(title);
        task.setPriority("中");
        task.setStatus(status);
        taskMapper.insert(task);
        return task;
    }
}
