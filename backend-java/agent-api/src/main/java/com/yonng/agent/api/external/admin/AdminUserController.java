package com.yonng.agent.api.external.admin;

import com.yonng.agent.api.config.CurrentUser;
import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.dto.user.UserInfo;
import com.yonng.agent.dto.user.UserManageRequest;
import com.yonng.agent.dto.user.UserManageResponse;
import com.yonng.agent.service.user.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外部用户管理接口。
 *
 * <p>调用方：平台管理台。路径位于 /api/admin/**，由 SecurityConfig 限制平台管理员访问。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResult<List<UserManageResponse>> listUsers(@RequestParam(required = false) Long customerId) {
        return ApiResult.ok(adminUserService.listUsers(customerId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<Long> createUser(@Valid @RequestBody UserManageRequest request, @CurrentUser UserInfo user) {
        return ApiResult.ok(adminUserService.createUser(request, user.getRole()));
    }

    @PutMapping("/{id}")
    public ApiStatus updateUser(@PathVariable Long id, @Valid @RequestBody UserManageRequest request) {
        adminUserService.updateUser(id, request);
        return ApiStatus.ok();
    }

    @PatchMapping("/{id}/status")
    public ApiStatus toggleStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        adminUserService.toggleUserStatus(id, enabled);
        return ApiStatus.ok();
    }
}
