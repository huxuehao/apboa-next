package com.hxh.apboa.dashboard.dataset.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 描述：按已保存数据集执行的请求（面板运行态取数）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class DatasetQueryRequest {
    /**
     * 命名参数
     */
    private Map<String, Object> params;
    /**
     * 行数上限，可空则使用默认取数上限
     */
    private Integer limit;
}
