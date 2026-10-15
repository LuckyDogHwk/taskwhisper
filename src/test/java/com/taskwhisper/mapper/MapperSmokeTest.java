package com.taskwhisper.mapper;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskwhisper.entity.Category;
import com.taskwhisper.entity.Task;
import com.taskwhisper.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础冒烟：H2(MODE=MySQL) 建表脚本可用、实体映射正确、审计字段自动填充、
 * 分页拦截器（jsqlparser 依赖）生效。
 */
@SpringBootTest
@ActiveProfiles("test")
class MapperSmokeTest {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @Test
    void insertAndReadBackWithAutoFillAndPagination() {
        User user = new User();
        user.setUsername("mapper_smoke");
        user.setPasswordHash("x");
        userMapper.insert(user);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();

        Category category = new Category();
        category.setUserId(user.getId());
        category.setName("工作");
        categoryMapper.insert(category);
        assertThat(category.getId()).isNotNull();

        Task task = new Task();
        task.setUserId(user.getId());
        task.setCategoryId(category.getId());
        task.setTitle("冒烟任务");
        task.setPriority("高");
        task.setStatus("待办");
        task.setDueTime(LocalDateTime.of(2026, 12, 31, 12, 0));
        taskMapper.insert(task);
        assertThat(task.getId()).isNotNull();
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isNotNull();

        Task readBack = taskMapper.selectById(task.getId());
        assertThat(readBack.getTitle()).isEqualTo("冒烟任务");
        assertThat(readBack.getPriority()).isEqualTo("高");
        assertThat(readBack.getCategoryId()).isEqualTo(category.getId());
        assertThat(readBack.getDueTime()).isEqualTo(LocalDateTime.of(2026, 12, 31, 12, 0));

        // 分页插件可用（mybatis-plus-jsqlparser 显式依赖的回归锚点）
        Page<Task> page = taskMapper.selectPage(new Page<>(1, 10), null);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(mybatisPlusInterceptor).isNotNull();
    }

    @Test
    void categoryUniqueConstraintEnforced() {
        User user = new User();
        user.setUsername("mapper_unique");
        user.setPasswordHash("x");
        userMapper.insert(user);

        Category c1 = new Category();
        c1.setUserId(user.getId());
        c1.setName("学习");
        categoryMapper.insert(c1);

        Category c2 = new Category();
        c2.setUserId(user.getId());
        c2.setName("学习");
        // (user_id, name) 组合唯一键生效 → 第二次插入必须失败
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> categoryMapper.insert(c2));
    }
}
