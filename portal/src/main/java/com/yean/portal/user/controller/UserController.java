package com.yean.portal.user.controller;

import com.yean.common.operatelog.OperateLog;
import com.yean.common.result.R;
import com.yean.portal.user.dto.AuthenticateDTO;
import com.yean.portal.user.dto.GrantRoleDTO;
import com.yean.portal.user.dto.PermissionCheckDTO;
import com.yean.portal.user.dto.PermissionCheckVO;
import com.yean.portal.user.dto.UserCreateDTO;
import com.yean.portal.user.dto.UserQueryDTO;
import com.yean.portal.user.dto.UserUpdateDTO;
import com.yean.portal.user.dto.UserVO;
import com.yean.portal.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "用户")
@Validated
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "登录校验（不签发 Token）")
    @PostMapping("/authenticate")
    public R<UserVO> authenticate(@Valid @RequestBody AuthenticateDTO dto) {
        return R.ok(userService.authenticate(dto));
    }

    @Operation(summary = "判断用户是否具备指定权限")
    @GetMapping("/permission/check")
    public R<PermissionCheckVO> checkPermission(@Valid PermissionCheckDTO dto) {
        return R.ok(userService.checkPermission(dto));
    }

    @Operation(summary = "按 ID 查询用户")
    @GetMapping("/info/{id}")
    public R<UserVO> info(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @Operation(summary = "用户列表")
    @GetMapping("/list")
    public R<List<UserVO>> list(UserQueryDTO query) {
        return R.ok(userService.list(query));
    }

    @OperateLog(module = "user", action = "create")
    @Operation(summary = "新增用户")
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        return R.ok(userService.create(dto));
    }

    @OperateLog(module = "user", action = "update")
    @Operation(summary = "修改用户")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return R.ok();
    }

    @OperateLog(module = "user", action = "grantRoles")
    @Operation(summary = "管理员授权：全量覆盖用户角色")
    @PutMapping("/{id}/roles")
    public R<Void> grantRoles(@PathVariable Long id, @Valid @RequestBody GrantRoleDTO dto) {
        userService.grantRoles(id, dto);
        return R.ok();
    }

    @OperateLog(module = "user", action = "delete")
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
