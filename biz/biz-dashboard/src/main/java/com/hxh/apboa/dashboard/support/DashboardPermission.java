package com.hxh.apboa.dashboard.support;

import com.hxh.apboa.common.util.UserUtils;

/**
 * 描述：Dashboard 权限校验工具。租户模板与数据集的写操作需要管理员角色。
 *
 * @author huxuehao
 **/
public final class DashboardPermission {
    private DashboardPermission() {
    }

    /**
     * 断言当前用户为租户管理员，否则抛出异常
     */
    public static void requireAdmin() {
        if (!UserUtils.isAdmin()) {
            throw new RuntimeException("需要租户管理员权限");
        }
    }
}
