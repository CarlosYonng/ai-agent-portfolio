package com.yonng.agent.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.user.SysCustomer;
import com.yonng.agent.domain.user.SysUser;
import com.yonng.agent.dto.user.UserManageRequest;
import com.yonng.agent.dto.user.UserManageResponse;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.mapper.user.SysCustomerMapper;
import com.yonng.agent.mapper.user.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员用户管理服务。
 *
 * <p>提供用户列表、创建、编辑、禁用等功能，仅 SUPER_ADMIN 可访问。</p>
 */
@Service
public class AdminUserService {

    private final SysUserMapper sysUserMapper;
    private final SysCustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(SysUserMapper sysUserMapper, SysCustomerMapper customerMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.customerMapper = customerMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserManageResponse> listUsers(Long customerId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getCustomerId, SysUser::getUsername,
                        SysUser::getDisplayName, SysUser::getEmail, SysUser::getRoleCode,
                        SysUser::getStatus, SysUser::getCreatedAt)
                .orderByDesc(SysUser::getId);
        if (customerId != null) {
            wrapper.eq(SysUser::getCustomerId, customerId);
        }
        return sysUserMapper.selectList(wrapper)
                .stream()
                .map(UserManageResponse::from)
                .toList();
    }

    public Long createUser(UserManageRequest request, String creatorRole) {
        validateCreateRequest(request);
        // 权限检查：SUPER_ADMIN 可创建任何角色，PLATFORM_ADMIN 只能创建客户级角色
        if (!"SUPER_ADMIN".equals(creatorRole)) {
            if ("SUPER_ADMIN".equals(request.getRoleCode()) || "PLATFORM_ADMIN".equals(request.getRoleCode())) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权创建平台级账号");
            }
        }
        SysUser user = new SysUser();
        user.setCustomerId(customerIdForRole(request.getRoleCode(), request.getCustomerId()));
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRoleCode(request.getRoleCode());
        user.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USER_USERNAME_EXISTS);
        }
        return user.getId();
    }

    public void updateUser(Long id, UserManageRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getRoleCode() != null) {
            ensureKnownRole(request.getRoleCode());
            user.setRoleCode(request.getRoleCode());
            user.setCustomerId(customerIdForRole(request.getRoleCode(), request.getCustomerId()));
        } else if (request.getCustomerId() != null) {
            ensureActiveCustomer(request.getCustomerId());
            user.setCustomerId(request.getCustomerId());
        }
        if (request.getStatus() != null) user.setStatus(request.getStatus());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        sysUserMapper.updateById(user);
    }

    public void toggleUserStatus(Long id, boolean enabled) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setStatus(enabled ? "ACTIVE" : "DISABLED");
        sysUserMapper.updateById(user);
    }

    private void validateCreateRequest(UserManageRequest request) {
        ensureKnownRole(request.getRoleCode());
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_REQUIRED);
        }
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.USER_USERNAME_EXISTS);
        }
        if (isCustomerRole(request.getRoleCode())) {
            ensureActiveCustomer(request.getCustomerId());
        }
    }

    private Long customerIdForRole(String roleCode, Long customerId) {
        if (isCustomerRole(roleCode)) {
            ensureActiveCustomer(customerId);
            return customerId;
        }
        // 平台管理员统一归属 PLATFORM 客户，数据范围过滤通过 RoleAccess 控制（平台角色看全部），
        // 平台管理员不再以 customerId IS NULL 作为"全部可见"的信号。
        SysCustomer platform = customerMapper.selectOne(
                new LambdaQueryWrapper<SysCustomer>().eq(SysCustomer::getInviteCode, "PLATFORM"));
        if (platform == null) {
            throw new BusinessException(ErrorCode.PLATFORM_CUSTOMER_NOT_FOUND);
        }
        return platform.getId();
    }

    private void ensureActiveCustomer(Long customerId) {
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_REQUIRED);
        }
        SysCustomer customer = customerMapper.selectById(customerId);
        if (customer == null || !"ACTIVE".equals(customer.getStatus())) {
            throw new BusinessException(ErrorCode.CUSTOMER_INACTIVE);
        }
    }

    private void ensureKnownRole(String roleCode) {
        if (!"USER".equals(roleCode)
                && !"CUSTOMER_ADMIN".equals(roleCode)
                && !"PLATFORM_ADMIN".equals(roleCode)
                && !"SUPER_ADMIN".equals(roleCode)) {
            throw new BusinessException(ErrorCode.USER_ROLE_INVALID);
        }
    }

    private boolean isCustomerRole(String roleCode) {
        return "USER".equals(roleCode) || "CUSTOMER_ADMIN".equals(roleCode);
    }
}
