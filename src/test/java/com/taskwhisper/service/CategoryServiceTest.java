package com.taskwhisper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.taskwhisper.dto.CategoryCreateRequest;
import com.taskwhisper.dto.CategoryResponse;
import com.taskwhisper.entity.Category;
import com.taskwhisper.entity.User;
import com.taskwhisper.mapper.CategoryMapper;
import com.taskwhisper.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CategoryService 集成测试：真实 H2(MODE=MySQL)，直接注入 Service 与 Mapper 造数。
 * 覆盖：创建/列表、幂等、findOrCreate 复用与新建、空白名、跨用户同名隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private UserMapper userMapper;

    private Long newUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("x");
        userMapper.insert(user);
        return user.getId();
    }

    @Test
    void createAndListOrderedByIdAsc() {
        Long userId = newUser("cat_svc_list");

        CategoryResponse first = categoryService.create(userId, new CategoryCreateRequest("工作"));
        CategoryResponse second = categoryService.create(userId, new CategoryCreateRequest("学习"));

        assertThat(first.id()).isNotNull();
        assertThat(first.name()).isEqualTo("工作");
        assertThat(second.name()).isEqualTo("学习");

        List<CategoryResponse> list = categoryService.list(userId);
        assertThat(list).extracting(CategoryResponse::name).containsExactly("工作", "学习");
        assertThat(list.get(0).id()).isLessThan(list.get(1).id());
    }

    @Test
    void createIsIdempotentForSameNameAfterTrim() {
        Long userId = newUser("cat_svc_idempotent");

        CategoryResponse first = categoryService.create(userId, new CategoryCreateRequest("  生活  "));
        CategoryResponse second = categoryService.create(userId, new CategoryCreateRequest("生活"));

        assertThat(second.id()).isEqualTo(first.id());
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(Category::getName, "生活"));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void findOrCreateReusesExistingAndCreatesNew() {
        Long userId = newUser("cat_svc_find");

        Category first = categoryService.findOrCreate(userId, "健康");
        assertThat(first.getId()).isNotNull();
        assertThat(first.getName()).isEqualTo("健康");
        assertThat(first.getUserId()).isEqualTo(userId);

        Category again = categoryService.findOrCreate(userId, "健康");
        assertThat(again.getId()).isEqualTo(first.getId());

        Category other = categoryService.findOrCreate(userId, "理财");
        assertThat(other.getId()).isNotEqualTo(first.getId());
        assertThat(other.getName()).isEqualTo("理财");

        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId));
        assertThat(count).isEqualTo(2);
    }

    @Test
    void findOrCreateReturnsNullForNullOrBlank() {
        Long userId = newUser("cat_svc_blank");

        assertThat(categoryService.findOrCreate(userId, null)).isNull();
        assertThat(categoryService.findOrCreate(userId, "")).isNull();
        assertThat(categoryService.findOrCreate(userId, "   ")).isNull();
    }

    @Test
    void sameNameAcrossUsersIsIndependent() {
        Long userA = newUser("cat_svc_user_a");
        Long userB = newUser("cat_svc_user_b");

        CategoryResponse a = categoryService.create(userA, new CategoryCreateRequest("工作"));
        CategoryResponse b = categoryService.create(userB, new CategoryCreateRequest("工作"));

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(categoryService.list(userA)).hasSize(1);
        assertThat(categoryService.list(userB)).hasSize(1);
        assertThat(categoryService.list(userA).get(0).id()).isEqualTo(a.id());
        assertThat(categoryService.list(userB).get(0).id()).isEqualTo(b.id());
    }
}
