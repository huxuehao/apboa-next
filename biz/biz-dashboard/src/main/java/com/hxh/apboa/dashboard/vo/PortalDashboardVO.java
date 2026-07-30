package com.hxh.apboa.dashboard.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：门户解析结果。返回当前用户生效的 Dashboard 与其来源信息。
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class PortalDashboardVO {
    /**
     * 生效的模板 ID
     */
    private Long dashboardId;
    /**
     * 来源：TEMPLATE（直接用模板）或 PERSONAL（个人副本）
     */
    private String source;
    /**
     * 模板当前版本
     */
    private String templateVersion;
    /**
     * 个人副本克隆时基于的模板版本
     */
    private String basedVersion;
    /**
     * 模板是否已升级（个人副本落后，可提示重置）
     */
    private boolean stale;
    /**
     * 生效的 DSL 配置
     */
    private Object config;
}
