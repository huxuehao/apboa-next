package com.hxh.apboa.dashboard.dataset.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：数据集结果列元信息
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class ColumnMeta {
    /**
     * 列名
     */
    private String name;
    /**
     * 列类型（JDBC 列类型名，供前端字段映射参考）
     */
    private String type;

    public ColumnMeta() {
    }

    public ColumnMeta(String name, String type) {
        this.name = name;
        this.type = type;
    }
}
