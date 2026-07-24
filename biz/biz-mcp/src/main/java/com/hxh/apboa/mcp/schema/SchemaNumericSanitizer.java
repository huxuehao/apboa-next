package com.hxh.apboa.mcp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 依据 JSON Schema draft 2020-12 validation vocabulary meta-schema，把工具 schema 里
 * 「值必须是数字、却被第三方 MCP 序列化成字符串」的关键字校正回数字。
 *
 * <p>背景：部分 MCP（如 firecrawl）把 {@code "maximum":"9007199254740991"} 写成了字符串，
 * 本地模型（llama.cpp 系）编译 grammar 时按 nlohmann/json 严格解析，抛
 * {@code type_error.302 (type must be number, but is string)} 导致上游返回 HTTP 400。
 *
 * <p>数值关键字集<b>不是手写</b>，而是启动时从 meta-schema 的 {@code properties} + {@code $defs}
 * 派生（换 JSON Schema draft 只需替换 resources 下的 meta-schema 文件）。清洗在工具目录
 * <b>入库时</b>做一次，agent 运行时只读库、不做任何处理。
 *
 * @author psh
 */
@Component
public class SchemaNumericSanitizer {

    private static final Logger log = LoggerFactory.getLogger(SchemaNumericSanitizer.class);
    private static final String META_SCHEMA = "/json-schema/draft2020-12-validation.json";
    private static final int MAX_REF_DEPTH = 8;

    /** 值必须为 number/integer 的关键字集；来自 meta-schema，非手写硬编码。 */
    private final Set<String> numericKeywords;

    public SchemaNumericSanitizer(ObjectMapper objectMapper) {
        this.numericKeywords = deriveNumericKeywords(objectMapper);
        log.info("SchemaNumericSanitizer 从 meta-schema 派生数值关键字 {} 个: {}",
                numericKeywords.size(), numericKeywords);
    }

    /** 遍历 meta-schema 的 properties，解析每个关键字期望的类型，收集 number/integer 的关键字。 */
    private Set<String> deriveNumericKeywords(ObjectMapper objectMapper) {
        try (InputStream in = SchemaNumericSanitizer.class.getResourceAsStream(META_SCHEMA)) {
            if (in == null) {
                log.error("meta-schema 未在 classpath 找到: {}；数值清洗将降级为不处理", META_SCHEMA);
                return Set.of();
            }
            JsonNode meta = objectMapper.readTree(in);
            JsonNode properties = meta.path("properties");
            JsonNode defs = meta.path("$defs");
            Set<String> result = new LinkedHashSet<>();
            properties.fields().forEachRemaining(entry -> {
                String type = resolveType(entry.getValue(), defs, 0);
                if ("number".equals(type) || "integer".equals(type)) {
                    result.add(entry.getKey());
                }
            });
            return Set.copyOf(result);
        } catch (Exception e) {
            log.error("加载 meta-schema {} 失败；数值清洗将降级为不处理", META_SCHEMA, e);
            return Set.of();
        }
    }

    /** 解析关键字定义的期望类型：直接 type，或经 $ref（可能多层）指向 $defs 再取 type。 */
    private String resolveType(JsonNode def, JsonNode defs, int depth) {
        if (def == null || depth > MAX_REF_DEPTH) {
            return null;
        }
        JsonNode typeNode = def.path("type");
        if (typeNode.isTextual()) {
            return typeNode.asText();
        }
        JsonNode refNode = def.path("$ref");
        if (refNode.isTextual()) {
            String ref = refNode.asText();                        // 形如 "#/$defs/nonNegativeInteger"
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            return resolveType(defs.path(name), defs, depth + 1);
        }
        return null;
    }

    /**
     * 递归把 schema 里数值关键字的「字符串数字」转成数字；原地修改并返回同一节点。
     * 其它字段（含 default/const/enum/pattern 等）一律不动，仅递归其子树。
     */
    public JsonNode sanitize(JsonNode node) {
        if (node == null || numericKeywords.isEmpty()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fieldNames::add);
            for (String field : fieldNames) {
                JsonNode value = obj.get(field);
                if (numericKeywords.contains(field) && value != null && value.isTextual()) {
                    JsonNode number = toNumber(value.asText());
                    if (number != null) {
                        obj.set(field, number);
                        continue;
                    }
                }
                obj.set(field, sanitize(value));
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, sanitize(arr.get(i)));
            }
            return arr;
        }
        return node;
    }

    private JsonNode toNumber(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            if (trimmed.matches("[+-]?\\d+")) {
                return LongNode.valueOf(Long.parseLong(trimmed));
            }
            return DoubleNode.valueOf(Double.parseDouble(trimmed));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
