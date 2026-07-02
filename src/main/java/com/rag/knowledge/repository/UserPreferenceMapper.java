package com.rag.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.knowledge.domain.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
}
