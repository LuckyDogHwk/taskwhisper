package com.taskwhisper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskwhisper.common.BizException;
import com.taskwhisper.common.ErrorCode;
import com.taskwhisper.common.PageResponse;
import com.taskwhisper.dto.TaskCreateRequest;
import com.taskwhisper.dto.TaskQueryRequest;
import com.taskwhisper.dto.TaskResponse;
import com.taskwhisper.dto.TaskUpdateRequest;
import com.taskwhisper.entity.Category;
import com.taskwhisper.entity.Task;
import com.taskwhisper.mapper.CategoryMapper;
import com.taskwhisper.mapper.TaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务 CRUD 与过滤分页。
 * 所有操作强制 userId 作用域（防横向越权）：
 * 分类不属于当前用户抛 IllegalArgumentException("分类不存在")，
 * 任务不存在或非本人抛 BizException(NOT_FOUND)。
 */
@Service
public class TaskService {

    /** 优先级枚举 */
    private static final Set<String> PRIORITIES = Set.of("高", "中", "低");
    /** 状态枚举 */
    private static final Set<String> STATUSES = Set.of("待办", "进行中", "已完成");

    private final TaskMapper taskMapper;
    private final CategoryMapper categoryMapper;

    public TaskService(TaskMapper taskMapper, CategoryMapper categoryMapper) {
        this.taskMapper = taskMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 新建任务。priority/status 为 null 时默认 中/待办。
     *
     * @throws IllegalArgumentException categoryId 非空但不属于该用户，或枚举值非法
     */
    public TaskResponse create(Long userId, TaskCreateRequest req) {
        Long categoryId = req.categoryId();
        if (categoryId != null) {
            requireOwnCategory(userId, categoryId);
        }
        Task task = new Task();
        task.setUserId(userId);
        task.setCategoryId(categoryId);
        task.setTitle(req.title());
        task.setDescription(req.description());
        task.setPriority(normalizePriority(req.priority()));
        task.setStatus(normalizeStatus(req.status()));
        task.setDueTime(req.dueTime());
        taskMapper.insert(task);
        return toResponse(task, categoryNamesFor(List.of(task)));
    }

    /**
     * 过滤分页：status/categoryId/priority 等值过滤，按 id 倒序（最新在前）。
     * 分类过滤会额外校验归属：不属于当前用户直接返回 total=0 空页。
     *
     * @throws IllegalArgumentException page &lt; 1 或 size &lt; 1 或 size &gt; 100
     */
    public PageResponse<TaskResponse> page(Long userId, TaskQueryRequest q) {
        if (q.page() < 1 || q.size() < 1 || q.size() > 100) {
            throw new IllegalArgumentException("页码必须 ≥1，每页大小必须为 1~100");
        }
        if (q.categoryId() != null) {
            Category category = categoryMapper.selectById(q.categoryId());
            if (category == null || !Objects.equals(category.getUserId(), userId)) {
                return PageResponse.of(0, List.of());
            }
        }
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getUserId, userId)
                .eq(q.status() != null, Task::getStatus, q.status())
                .eq(q.categoryId() != null, Task::getCategoryId, q.categoryId())
                .eq(q.priority() != null, Task::getPriority, q.priority())
                .orderByDesc(Task::getId);
        Page<Task> page = taskMapper.selectPage(new Page<>(q.page(), q.size()), wrapper);
        Map<Long, String> categoryNames = categoryNamesFor(page.getRecords());
        List<TaskResponse> records = page.getRecords().stream()
                .map(task -> toResponse(task, categoryNames))
                .toList();
        return PageResponse.of(page.getTotal(), records);
    }

    /**
     * 查询单个任务。不存在或非本人 → BizException(NOT_FOUND)。
     */
    public TaskResponse get(Long userId, Long taskId) {
        Task task = requireOwnTask(userId, taskId);
        return toResponse(task, categoryNamesFor(List.of(task)));
    }

    /**
     * 部分更新：非 null 字段才覆盖；categoryId 非空时校验分类归属；枚举非空时校验合法性。
     * 任务不存在或非本人 → BizException(NOT_FOUND)。
     */
    public TaskResponse update(Long userId, Long taskId, TaskUpdateRequest req) {
        Task task = requireOwnTask(userId, taskId);
        if (req.title() != null) {
            task.setTitle(req.title());
        }
        if (req.description() != null) {
            task.setDescription(req.description());
        }
        if (req.categoryId() != null) {
            requireOwnCategory(userId, req.categoryId());
            task.setCategoryId(req.categoryId());
        }
        if (req.priority() != null) {
            task.setPriority(normalizePriority(req.priority()));
        }
        if (req.status() != null) {
            task.setStatus(normalizeStatus(req.status()));
        }
        if (req.dueTime() != null) {
            task.setDueTime(req.dueTime());
        }
        taskMapper.updateById(task);
        return toResponse(task, categoryNamesFor(List.of(task)));
    }

    /**
     * 删除任务。不存在或非本人 → BizException(NOT_FOUND)。
     */
    public void delete(Long userId, Long taskId) {
        Task task = requireOwnTask(userId, taskId);
        taskMapper.deleteById(task.getId());
    }

    /** 实体 → 响应；categoryName 从未分类名映射中取（未分类为 null）。 */
    private TaskResponse toResponse(Task task, Map<Long, String> categoryNames) {
        String categoryName = task.getCategoryId() == null ? null : categoryNames.get(task.getCategoryId());
        return new TaskResponse(
                task.getId(),
                task.getCategoryId(),
                categoryName,
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getDueTime(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    /** 取任务并校验归属，不存在或非本人 → BizException(NOT_FOUND)。 */
    private Task requireOwnTask(Long userId, Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return task;
    }

    /** 校验分类存在且属于当前用户，否则 IllegalArgumentException("分类不存在")。 */
    private void requireOwnCategory(Long userId, Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || !Objects.equals(category.getUserId(), userId)) {
            throw new IllegalArgumentException("分类不存在");
        }
    }

    /** priority 规范化：null → 中；非法值 → IllegalArgumentException。 */
    private String normalizePriority(String priority) {
        if (priority == null) {
            return "中";
        }
        if (!PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("优先级只能是 高/中/低");
        }
        return priority;
    }

    /** status 规范化：null → 待办；非法值 → IllegalArgumentException。 */
    private String normalizeStatus(String status) {
        if (status == null) {
            return "待办";
        }
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("状态只能是 待办/进行中/已完成");
        }
        return status;
    }

    /** 批量取任务涉及的分类名映射（避免 N+1；无分类引用时返回空 Map）。 */
    private Map<Long, String> categoryNamesFor(List<Task> tasks) {
        List<Long> categoryIds = tasks.stream()
                .map(Task::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }
}
