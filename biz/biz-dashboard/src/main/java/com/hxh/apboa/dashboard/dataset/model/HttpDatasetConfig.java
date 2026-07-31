package com.hxh.apboa.dashboard.dataset.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：HTTP 数据集配置（对应 DashboardDataset.httpConfig JSON）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class HttpDatasetConfig {
    /**
     * 请求地址（仅 GET）
     */
    private String url;
    /**
     * 查询参数定义
     */
    private List<HttpParam> queries = new ArrayList<>();
    /**
     * 固定请求头
     */
    private List<HttpHeader> headers = new ArrayList<>();
    /**
     * 结果数组定位路径（点分隔），为空则使用整个响应体
     */
    private String dataPath;

    /**
     * 查询参数：value 支持 :name 模板（绑定筛选/系统参数），未命中时回退 default
     */
    @Getter
    @Setter
    public static class HttpParam {
        private String key;
        private String value;
        @JsonProperty("default")
        private String defaultValue;
    }

    /**
     * 固定请求头
     */
    @Getter
    @Setter
    public static class HttpHeader {
        private String key;
        private String value;
    }
}
