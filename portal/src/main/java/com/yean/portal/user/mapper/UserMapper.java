package com.yean.portal.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yean.portal.user.dto.UserQueryDTO;
import com.yean.portal.user.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper extends BaseMapper<User> {

    User selectByUsername(@Param("username") String username);

    IPage<User> selectUserList(IPage<User> page, @Param("q") UserQueryDTO query);

    int countByDeptId(@Param("deptId") Long deptId);
}
