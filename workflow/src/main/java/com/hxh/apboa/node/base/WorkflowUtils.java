package com.hxh.apboa.node.base;

import com.hxh.apboa.node.base.inputout.OutputConfig;

import java.util.List;

/**
 * 描述：
 *
 * @author huxuehao
 **/
public class WorkflowUtils {

    /**
     * 基于运行时值推断变量类型，instanceof 按使用频率排序以优化分支预测。
     */
    public static OutputConfig.VariableType inferType(Object value) {
        if (value instanceof String)  return OutputConfig.VariableType.String;
        if (value instanceof Integer) return OutputConfig.VariableType.Integer;
        if (value instanceof Long)    return OutputConfig.VariableType.Long;
        if (value instanceof Double)  return OutputConfig.VariableType.Double;
        if (value instanceof Float)   return OutputConfig.VariableType.Float;
        if (value instanceof Boolean) return OutputConfig.VariableType.Boolean;
        if (value instanceof List)    return OutputConfig.VariableType.Array;
        return OutputConfig.VariableType.Object;
    }
}
