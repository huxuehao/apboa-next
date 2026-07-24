package com.hxh.apboa.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 扩展配置解析工具
 * 用于从 extendConfig (JsonNode) 中解析 headers、queryParams、bodyParams
 *
 * @author huxuehao
 */
public final class ExtendConfigHelper {

    private ExtendConfigHelper() {
    }

    /**
     * 从 JsonNode 解析并填充到 configWrapper
     * 仅在 configWrapper 中对应字段为空时填充
     */
    public static void fillIfAbsent(ModelConfigWrapper configWrapper, JsonNode extendConfig) {
        if (extendConfig == null || extendConfig.isNull() || !extendConfig.isObject()) {
            return;
        }
        if (configWrapper.getHeaders() == null && extendConfig.has("headers")) {
            configWrapper.setHeaders(parseStringMap(extendConfig.get("headers")));
        }
        if (configWrapper.getQueryParams() == null && extendConfig.has("queryParams")) {
            configWrapper.setQueryParams(parseStringMap(extendConfig.get("queryParams")));
        }
        if (configWrapper.getBodyParams() == null && extendConfig.has("bodyParams")) {
            configWrapper.setBodyParams(parseObjectMap(extendConfig.get("bodyParams")));
        }
        if (configWrapper.getFixedSystemMessage() == null && extendConfig.has("fixedSystemMessage")) {
            configWrapper.setFixedSystemMessage(parseBoolean(extendConfig.get("fixedSystemMessage")));
        }
        // 思考参数（开/关分别注入的请求体参数，数据驱动；代码不认识具体参数名）
        if (extendConfig.has("thinkingParams") && extendConfig.get("thinkingParams").isObject()) {
            JsonNode tp = extendConfig.get("thinkingParams");
            if (configWrapper.getThinkingParamsOn() == null && tp.has("on")) {
                configWrapper.setThinkingParamsOn(parseObjectMap(tp.get("on")));
            }
            if (configWrapper.getThinkingParamsOff() == null && tp.has("off")) {
                configWrapper.setThinkingParamsOff(parseObjectMap(tp.get("off")));
            }
        }
    }

    /**
     * 强制填充（覆盖已有值），用于 agent 级 modelParamsOverride
     */
    public static void fillOverride(ModelConfigWrapper configWrapper, JsonNode extendConfig) {
        if (extendConfig == null || extendConfig.isNull() || !extendConfig.isObject()) {
            return;
        }
        if (extendConfig.has("headers")) {
            configWrapper.setHeaders(parseStringMap(extendConfig.get("headers")));
        }
        if (extendConfig.has("queryParams")) {
            configWrapper.setQueryParams(parseStringMap(extendConfig.get("queryParams")));
        }
        if (extendConfig.has("bodyParams")) {
            configWrapper.setBodyParams(parseObjectMap(extendConfig.get("bodyParams")));
        }
        if (extendConfig.has("fixedSystemMessage")) {
            configWrapper.setFixedSystemMessage(parseBoolean(extendConfig.get("fixedSystemMessage")));
        }
        // 思考参数（agent 级强制覆盖）
        if (extendConfig.has("thinkingParams") && extendConfig.get("thinkingParams").isObject()) {
            JsonNode tp = extendConfig.get("thinkingParams");
            if (tp.has("on")) {
                configWrapper.setThinkingParamsOn(parseObjectMap(tp.get("on")));
            }
            if (tp.has("off")) {
                configWrapper.setThinkingParamsOff(parseObjectMap(tp.get("off")));
            }
        }
    }

    /**
     * 将扁平覆盖参数并入 configWrapper.bodyParams（覆盖同名 key），
     * 用于 agent 级 TTS/ASR 参数覆盖（如音色 {"voice":"Cherry"}）。override 为空则不改动。
     * 复制出新 map，避免污染模型层可能共享的 bodyParams。
     */
    public static void mergeBodyParams(ModelConfigWrapper configWrapper, JsonNode override) {
        if (override == null || override.isNull() || !override.isObject() || override.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new HashMap<>();
        Map<String, Object> existing = configWrapper.getBodyParams();
        if (existing != null) {
            merged.putAll(existing);
        }
        merged.putAll(parseObjectMap(override));
        configWrapper.setBodyParams(merged);
    }

    public static Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode valueNode = node.get(key);
            if (valueNode != null && !valueNode.isNull()) {
                map.put(key, valueNode.asText());
            }
        }
        return map.isEmpty() ? Map.of() : map;
    }

    public static boolean parseBoolean(JsonNode node) {
        if (node == null) {
            return false;
        }

        return node.isBoolean() && node.asBoolean(false);
    }

    public static Map<String, Object> parseObjectMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode valueNode = node.get(key);
            if (valueNode != null && !valueNode.isNull()) {
                map.put(key, jsonNodeToObject(valueNode));
            }
        }
        return map.isEmpty() ? Map.of() : map;
    }

    private static Object jsonNodeToObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            try {
                // 尝试将字符串转对象
                return JsonUtils.parse(node.asText());
            } catch (Exception e) {
                return node.asText();
            }

        }
        if (node.isNumber()) {
            if (node.isInt()) {
                return node.asInt();
            }
            if (node.isLong()) {
                return node.asLong();
            }
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            return JsonUtils.parse(node.toString(), java.util.List.class);
        }
        if (node.isObject()) {
            return JsonUtils.parse(node.toString(), Map.class);
        }
        return node.asText();
    }
}
