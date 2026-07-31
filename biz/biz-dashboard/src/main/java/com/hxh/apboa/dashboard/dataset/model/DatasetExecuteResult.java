package com.hxh.apboa.dashboard.dataset.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 描述：数据集执行结果
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class DatasetExecuteResult {
    /**
     * 结果列信息
     */
    private List<ColumnMeta> columns;
    /**
     * 结果行（每行为列名到值的映射）
     */
    private List<Map<String, Object>> rows;
    /**
     * 返回行数
     */
    private int rowCount;
    /**
     * 执行耗时（毫秒）
     */
    private long elapsedMs;
    /**
     * 是否因行数上限被截断
     */
    private boolean truncated;
    /**
     * 实际执行的最终 SQL（租户改写后），仅供审计日志使用，不外露给前端
     */
    @JsonIgnore
    private String executedSql;
}
