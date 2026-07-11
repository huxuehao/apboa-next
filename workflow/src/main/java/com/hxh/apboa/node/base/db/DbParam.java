package com.hxh.apboa.node.base.db;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：DB 节点参数项
 * 每个参数由 value（值，支持 Velocity 动态变量语法如 ${age}）和 type（类型提示）组成。
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class DbParam {

    /**
     * 参数值，支持 Velocity 动态变量语法，例如：
     * - 静态值："18"
     * - 上游节点输出："${age}"
     * - 表达式："${user.age > 18 ? 'adult' : 'minor'}"
     */
    private String value;

    /**
     * 参数类型提示：String、Integer、Long、Double、Boolean 等
     */
    private String type;
}
