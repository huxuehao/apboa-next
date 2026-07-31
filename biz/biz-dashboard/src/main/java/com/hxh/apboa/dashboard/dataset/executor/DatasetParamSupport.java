package com.hxh.apboa.dashboard.dataset.executor;

import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述：数据集参数装配支持。统一"用户参数 + 系统保留参数"的合并规则，供 SQL / HTTP 执行器共用。
 *
 * @author huxuehao
 **/
public final class DatasetParamSupport {
    private DatasetParamSupport() {
    }

    /**
     * 合并命名参数：先写入用户参数（数据集固定参数 + 面板私有筛选参数），
     * 后写入系统保留参数 currentTenantId / currentUserId，确保系统参数不可被外部伪造覆盖。
     */
    public static Map<String, Object> mergeParams(Map<String, Object> userParams) {
        Map<String, Object> params = new HashMap<>();
        if (userParams != null) {
            params.putAll(userParams);
        }
        params.put("currentTenantId", TenantUtils.getCurrentTenantId());
        params.put("currentUserId", UserUtils.getId());
        return params;
    }
}
