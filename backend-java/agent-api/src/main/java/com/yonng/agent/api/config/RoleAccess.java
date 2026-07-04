package com.yonng.agent.api.config;

import com.yonng.agent.dto.user.UserInfo;

/**
 * 角色数据范围判定。
 *
 * <p>内部角色看全平台；客户管理员看本客户全部；客户普通用户只看自己的历史数据。</p>
 */
public final class RoleAccess {

    private RoleAccess() {
    }

    public static boolean isInternal(UserInfo user) {
        return user != null && ("SUPER_ADMIN".equals(user.getRole()) || "PLATFORM_ADMIN".equals(user.getRole()));
    }

    public static boolean isCustomerAdmin(UserInfo user) {
        return user != null && "CUSTOMER_ADMIN".equals(user.getRole());
    }

    public static Long customerScope(UserInfo user) {
        return isInternal(user) ? null : user.getCustomerId();
    }

    public static Long customerScope(UserInfo user, Long filterCustomerId) {
        return isInternal(user) ? filterCustomerId : user.getCustomerId();
    }

    public static Long userScope(UserInfo user) {
        return isInternal(user) || isCustomerAdmin(user) ? null : user.getUserId();
    }
}
