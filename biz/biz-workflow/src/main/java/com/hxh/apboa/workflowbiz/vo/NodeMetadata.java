package com.hxh.apboa.workflowbiz.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class NodeMetadata {
    private String type;
    private String title;
    private String group;
    private String description;
    private Map<String, Object> defaultConfig;
    private List<FieldMetadata> fields;
    private List<String> outputs;
    private boolean branchable;

    public NodeMetadata(String type, String title, String group, String description, Map<String, Object> defaultConfig,
                        List<String> outputs, boolean branchable) {
        this(type, title, group, description, defaultConfig, fieldsFromDefaultConfig(defaultConfig), outputs, branchable);
    }

    public NodeMetadata(String type, String title, String group, String description, Map<String, Object> defaultConfig,
                        List<FieldMetadata> fields, List<String> outputs, boolean branchable) {
        this.type = type;
        this.title = title;
        this.group = group;
        this.description = description;
        this.defaultConfig = defaultConfig;
        this.fields = fields;
        this.outputs = outputs;
        this.branchable = branchable;
    }

    private static List<FieldMetadata> fieldsFromDefaultConfig(Map<String, Object> defaultConfig) {
        List<FieldMetadata> fields = new ArrayList<>();
        if (defaultConfig != null) {
            defaultConfig.forEach((name, value) -> fields.add(new FieldMetadata(
                    name,
                    value == null ? "string" : value.getClass().getSimpleName(),
                    false,
                    value,
                    List.of(),
                    controlFor(value)
            )));
        }
        return fields;
    }

    private static String controlFor(Object value) {
        if (value instanceof Boolean) {
            return "switch";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof List<?>) {
            return "json-list";
        }
        return "input";
    }

    @Getter
    @AllArgsConstructor
    public static class FieldMetadata {
        private String name;
        private String type;
        private boolean required;
        private Object defaultValue;
        private List<String> enumValues;
        private String control;
    }
}
