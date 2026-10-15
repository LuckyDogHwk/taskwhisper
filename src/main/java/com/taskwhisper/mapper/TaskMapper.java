package com.taskwhisper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskwhisper.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 各状态任务计数（统计模块手写 SQL）。
     *
     * @return 行形如 {status: "待办", cnt: 3}
     */
    List<Map<String, Object>> countByStatus(@Param("userId") Long userId);

    /**
     * 分类分布（LEFT JOIN 保留未分类桶）。
     *
     * @return 行形如 {category_id: 1, name: "工作", cnt: 3}；未分类桶 category_id/name 为 null
     */
    List<Map<String, Object>> countByCategory(@Param("userId") Long userId);
}
