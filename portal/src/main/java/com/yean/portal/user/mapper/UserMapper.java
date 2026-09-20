package com.yean.portal.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yean.portal.user.dto.UserQueryDTO;
import com.yean.portal.user.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    User selectByUsername(@Param("username") String username);

    List<User> selectUserList(UserQueryDTO query);

    int countByDeptId(@Param("deptId") Long deptId);
}
