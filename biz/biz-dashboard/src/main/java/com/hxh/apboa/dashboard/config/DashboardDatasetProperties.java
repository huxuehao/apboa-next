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
@ConfigurationProperties(prefix = "apboa.dashboard.dataset")
public class DashboardDatasetProperties {
    /**
     * 可查询对象白名单（表/视图名，小写匹配）。为空时不启用白名单校验（仅建议开发期），生产应显式配置。
     */
    private List<String> allowedTables = new ArrayList<>();
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
}
