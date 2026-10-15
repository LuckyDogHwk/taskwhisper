package com.taskwhisper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.taskwhisper.dto.CategoryCreateRequest;
import com.taskwhisper.dto.CategoryResponse;
import com.taskwhisper.entity.Category;
import com.taskwhisper.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户自定义分类。每个用户独立命名空间：(user_id, name) 唯一。
 * create 幂等：name trim 后同名已存在则直接返回已有分类。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /** 分类列表，按 id 升序。 */
    public List<CategoryResponse> list(Long userId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getUserId, userId).orderByAsc(Category::getId);
        return categoryMapper.selectList(wrapper).stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName()))
                .toList();
    }

    /**
     * 创建分类（幂等）：name trim 后同名已存在（用户范围内）→ 直接返回已有。
     */
    public CategoryResponse create(Long userId, CategoryCreateRequest req) {
        Category category = findOrCreate(userId, req.name());
        if (category == null) {
            throw new IllegalArgumentException("分类名不能为空");
        }
        return new CategoryResponse(category.getId(), category.getName());
    }

    /**
     * 按名查找或创建分类（供 NL 模块把解析出的分类名落成实体）。
     * name 为 null 或 trim 后空白 → 返回 null（表示未分类）；同名复用，否则新建。
     */
    public Category findOrCreate(Long userId, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String trimmed = name.trim();
        Category existing = findByName(userId, trimmed);
        if (existing != null) {
            return existing;
        }
        Category category = new Category();
        category.setUserId(userId);
        category.setName(trimmed);
        categoryMapper.insert(category);
        return category;
    }

    /** 用户范围内按名精确查找（组合唯一键保证至多一条）。 */
    private Category findByName(Long userId, String name) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getUserId, userId).eq(Category::getName, name);
        return categoryMapper.selectOne(wrapper);
    }
}
