package com.hxh.apboa.node.base;

import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.node.base.inputout.OutputConfig;

import java.util.List;
import java.util.Map;

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

    public static Object convertType(Object value, OutputConfig.VariableType type) {
        switch (type) {
            case String -> {
                return value.toString();
            }
            case Long -> {
                return Long.valueOf(value.toString());
            }
            case Integer -> {
                return Integer.valueOf(value.toString());
            }
            case Float -> {
                return Float.valueOf(value.toString());
            }
            case Double -> {
                return Double.valueOf(value.toString());
            }
            case Boolean -> {
                return Boolean.valueOf(value.toString());
            }
            case Array -> {
                try {
                    return JsonUtils.parse(value.toString(), List.class);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid array format: " + value, e);
                }
            }
            case Object -> {
                try {
                    return JsonUtils.parse(value.toString(), Map.class);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid object format: " + value, e);
                }
            }
        }

        return value;
    }
}
