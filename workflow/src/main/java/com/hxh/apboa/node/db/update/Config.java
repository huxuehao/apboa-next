package com.hxh.apboa.node.db.update;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.db.DbParam;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：数据库更新节点配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {

    /**
     * 数据源ID
     */
    private String datasourceId;

    /**
     * SQL 更新语句，支持参数占位符 ?
     * 参数值由 params 定义，通过 inputConfigs 动态解析后按顺序绑定到占位符
     */
    private String sql;

    /**
     * SQL 参数列表，每个参数包含 value（支持 Velocity 动态变量）和 type
     */
    private List<DbParam> params;

    /**
     * 模板格式化器类型，默认 VELOCITY
     */
    private FormatterType formatterType = FormatterType.VELOCITY;
}
