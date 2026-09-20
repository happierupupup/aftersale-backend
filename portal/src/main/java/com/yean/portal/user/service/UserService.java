package com.yean.portal.user.service;

import com.yean.portal.user.dto.AuthenticateDTO;
import com.yean.portal.user.dto.GrantRoleDTO;
import com.yean.portal.user.dto.PermissionCheckDTO;
import com.yean.portal.user.dto.PermissionCheckVO;
import com.yean.portal.user.dto.UserCreateDTO;
import com.yean.portal.user.dto.UserQueryDTO;
import com.yean.portal.user.dto.UserUpdateDTO;
import com.yean.portal.user.dto.UserVO;

import java.util.List;

public interface UserService {

    UserVO authenticate(AuthenticateDTO dto);

    UserVO getById(Long id);

    List<UserVO> list(UserQueryDTO query);

    Long create(UserCreateDTO dto);

    void update(Long id, UserUpdateDTO dto);

    void delete(Long id);

    void grantRoles(Long userId, GrantRoleDTO dto);

    PermissionCheckVO checkPermission(PermissionCheckDTO dto);

    boolean hasPermission(Long userId, String permission);
}
