package com.hxh.apboa.dashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：Dashboard 数据集执行配置
 * 可通过 apboa.dashboard.dataset.* 覆盖。
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dashboard.dataset")
public class DashboardDatasetProperties {
    /**
     * 可查询对象白名单（表/视图名，小写匹配）。为空且 whitelistRequired=true 时拒绝执行。
     */
    private List<String> allowedTables = new ArrayList<>();
    /**
     * 白名单为空时是否拒绝执行（生产应保持 true；开发期可配 false 回到告警跳过）
     */
    private boolean whitelistRequired = false;
    /**
     * 租户谓词注入豁免清单（真正的全局字典表，无 tenant_id 列）
     */
    private List<String> tenantExemptTables = new ArrayList<>();
    /**
     * 单次执行返回行数硬上限
     */
    private int maxRows = 1000;
    /**
     * 预览默认返回行数
     */
    private int previewLimit = 200;
    /**
     * 面板取数默认返回行数
     */
    private int queryLimit = 1000;
    /**
     * 查询超时（秒）
     */
    private int queryTimeoutSeconds = 10;
    /**
     * 单租户并发执行上限
     */
    private int maxConcurrentPerTenant = 8;
    /**
     * HTTP 数据集：连接超时（毫秒）
     */
    private int httpConnectTimeoutMs = 3000;
    /**
     * HTTP 数据集：读取超时（毫秒）
     */
    private int httpReadTimeoutMs = 8000;
    /**
     * HTTP 数据集：响应体大小上限（字节）
     */
    private long httpMaxResponseBytes = 2 * 1024 * 1024;
    /**
     * HTTP 数据集：主机白名单（小写域名/IP）。为空时不启用白名单（仅依赖 SSRF 基础防护）
     */
    private List<String> httpAllowedHosts = new ArrayList<>();
}
