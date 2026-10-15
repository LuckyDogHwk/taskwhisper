package com.taskwhisper.controller;

import com.taskwhisper.common.CurrentUser;
import com.taskwhisper.common.Result;
import com.taskwhisper.dto.CategoryCreateRequest;
import com.taskwhisper.dto.CategoryResponse;
import com.taskwhisper.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自定义分类。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<CategoryResponse>> list() {
        return Result.ok(categoryService.list(CurrentUser.id()));
    }

    @PostMapping
    public Result<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest req) {
        return Result.ok(categoryService.create(CurrentUser.id(), req));
    }
}
