package com.yean.portal.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.portal.user.entity.UserRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserRoleMapper extends BaseMapper<UserRole> {

    List<String> selectRolesByUserId(@Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);
}
