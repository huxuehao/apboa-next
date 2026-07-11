package com.hxh.apboa.node.base.db;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;

/**
 * 描述：数据库节点
 *
 * @author huxuehao
 **/
public interface DBNode {
    /**
     * 根据类型名称创建转换函数，使用 instanceof 快速路径避免不必要的解析。
     */
    default Function<Object, Object> createConverter(String type) {
        if (type == null || type.isEmpty()) {
            return v -> v;
        }
        return switch (type.toUpperCase()) {
            case "STRING" -> v -> {
                if (v instanceof JsonNode) {
                    return ((JsonNode) v).asText();
                }
                if (v == null) return null;
                return v.toString();
            };
            case "INTEGER", "INT" -> v -> {
                if (v instanceof JsonNode) {
                    return ((JsonNode) v).asInt();
                }
                if (v == null) return null;
                if (v instanceof Integer) return v;
                return Integer.valueOf(v.toString());
            };
            case "LONG" -> v -> {
                if (v instanceof JsonNode) {
                    return ((JsonNode) v).asLong();
                }
                if (v == null) return null;
                if (v instanceof Long) return v;
                return Long.valueOf(v.toString());
            };
            case "DOUBLE" -> v -> {
                if (v instanceof JsonNode) {
                    return ((JsonNode) v).asDouble();
                }
                if (v == null) return null;
                if (v instanceof Double) return v;
                return Double.valueOf(v.toString());
            };
            case "FLOAT" -> v -> {
                if (v instanceof JsonNode) {
                    return ((JsonNode) v).floatValue();
                }
                if (v == null) return null;
                if (v instanceof Float) return v;
                return Float.valueOf(v.toString());
            };
            case "BOOLEAN", "BOOL" -> v -> {
                if (v instanceof JsonNode) {
                    return ((JsonNode) v).asBoolean();
                }
                if (v == null) return null;
                if (v instanceof Boolean) return v;
                return Boolean.valueOf(v.toString());
            };
            default -> v -> v;
        };
    }
}
