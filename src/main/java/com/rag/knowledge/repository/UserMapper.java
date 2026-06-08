package com.rag.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.knowledge.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
