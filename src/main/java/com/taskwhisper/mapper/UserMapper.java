package com.taskwhisper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskwhisper.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
