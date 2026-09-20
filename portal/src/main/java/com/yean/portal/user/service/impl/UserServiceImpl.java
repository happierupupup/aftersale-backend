package com.yean.portal.user.service.impl;

import com.yean.common.enums.ErrorEnum;
import com.yean.common.enums.PermissionEnum;
import com.yean.common.enums.RoleEnum;
import com.yean.common.exception.BizException;
import com.yean.portal.config.RedisLock;
import com.yean.portal.dept.entity.Dept;
import com.yean.portal.dept.mapper.DeptMapper;
import com.yean.portal.user.dto.AuthenticateDTO;
import com.yean.portal.user.dto.GrantRoleDTO;
import com.yean.portal.user.dto.PermissionCheckDTO;
import com.yean.portal.user.dto.PermissionCheckVO;
import com.yean.portal.user.dto.UserCreateDTO;
import com.yean.portal.user.dto.UserQueryDTO;
import com.yean.portal.user.dto.UserUpdateDTO;
import com.yean.portal.user.dto.UserVO;
import com.yean.portal.user.entity.User;
import com.yean.portal.user.entity.UserRole;
import com.yean.portal.user.mapper.UserMapper;
import com.yean.portal.user.mapper.UserRoleMapper;
import com.yean.portal.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DeptMapper deptMapper;
    private final RedisLock redisLock;
    private final TransactionTemplate transactionTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public UserVO authenticate(AuthenticateDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorEnum.USER_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorEnum.USER_DISABLED);
        }
        return toVO(user);
    }

    @Override
    public UserVO getById(Long id) {
        return toVO(requireUser(id));
    }

    @Override
    public List<UserVO> list(UserQueryDTO query) {
        UserQueryDTO cond = query == null ? new UserQueryDTO() : query;
        return userMapper.selectUserList(cond).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Long create(UserCreateDTO dto) {
        return redisLock.execute("lock:user:username:" + dto.getUsername(), () ->
                transactionTemplate.execute(status -> {
                    checkDept(dto.getDeptId());
                    if (userMapper.selectByUsername(dto.getUsername()) != null) {
                        throw new BizException(ErrorEnum.USERNAME_EXISTS);
                    }
                    List<String> roles = normalizeRoles(dto.getRoles());
                    User user = new User();
                    user.setUsername(dto.getUsername());
                    user.setPassword(passwordEncoder.encode(dto.getPassword()));
                    user.setRole(roles.get(0));
                    user.setDeptId(dto.getDeptId());
                    user.setStatus(1);
                    userMapper.insert(user);
                    replaceRoles(user.getId(), roles);
                    return user.getId();
                }));
    }

    @Override
    public void update(Long id, UserUpdateDTO dto) {
        redisLock.execute("lock:user:" + id, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    User user = requireUser(id);
                    if (!CollectionUtils.isEmpty(dto.getRoles())) {
                        List<String> roles = normalizeRoles(dto.getRoles());
                        user.setRole(roles.get(0));
                        replaceRoles(id, roles);
                    }
                    if (dto.getDeptId() != null) {
                        checkDept(dto.getDeptId());
                        user.setDeptId(dto.getDeptId());
                    }
                    if (dto.getStatus() != null) {
                        user.setStatus(dto.getStatus());
                    }
                    if (StringUtils.hasText(dto.getPassword())) {
                        user.setPassword(passwordEncoder.encode(dto.getPassword()));
                    }
                    userMapper.updateById(user);
                }));
    }

    @Override
    public void delete(Long id) {
        redisLock.execute("lock:user:" + id, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    requireUser(id);
                    userRoleMapper.deleteByUserId(id);
                    userMapper.deleteById(id);
                }));
    }

    @Override
    public void grantRoles(Long userId, GrantRoleDTO dto) {
        redisLock.execute("lock:user:" + userId, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    requireUser(userId);
                    if (!hasPermission(dto.getOperatorId(), PermissionEnum.ROLE_GRANT.getCode())) {
                        throw new BizException(ErrorEnum.NO_GRANT_PERMISSION);
                    }
                    List<String> roles = normalizeRoles(dto.getRoles());
                    User user = requireUser(userId);
                    user.setRole(roles.get(0));
                    userMapper.updateById(user);
                    replaceRoles(userId, roles);
                }));
    }

    @Override
    public PermissionCheckVO checkPermission(PermissionCheckDTO dto) {
        requireUser(dto.getUserId());
        List<String> roles = userRoleMapper.selectRolesByUserId(dto.getUserId());
        boolean allowed = hasPermission(dto.getUserId(), dto.getPermission());
        return new PermissionCheckVO(dto.getUserId(), dto.getPermission(), allowed, roles);
    }

    @Override
    public boolean hasPermission(Long userId, String permission) {
        PermissionEnum perm = PermissionEnum.require(permission);
        List<String> roles = userRoleMapper.selectRolesByUserId(userId);
        return roles.stream()
                .map(RoleEnum::of)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .anyMatch(perm::grantedTo);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorEnum.USER_NOT_FOUND);
        }
        return user;
    }

    private void checkDept(Long deptId) {
        Dept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BizException(ErrorEnum.DEPT_NOT_FOUND);
        }
    }

    private List<String> normalizeRoles(List<String> raw) {
        if (CollectionUtils.isEmpty(raw)) {
            throw new BizException(ErrorEnum.ROLE_REQUIRED);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            unique.add(RoleEnum.require(item).name());
        }
        return new ArrayList<>(unique);
    }

    private void replaceRoles(Long userId, List<String> roles) {
        userRoleMapper.deleteByUserId(userId);
        for (String role : roles) {
            UserRole row = new UserRole();
            row.setUserId(userId);
            row.setRole(role);
            userRoleMapper.insert(row);
        }
    }

    private UserVO toVO(User user) {
        List<String> roles = userRoleMapper.selectRolesByUserId(user.getId());
        if (roles.isEmpty() && StringUtils.hasText(user.getRole())) {
            roles = List.of(user.getRole());
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(roles.isEmpty() ? user.getRole() : roles.get(0));
        vo.setRoles(roles);
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
