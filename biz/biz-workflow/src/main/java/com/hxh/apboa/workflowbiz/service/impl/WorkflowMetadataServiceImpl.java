package com.hxh.apboa.workflowbiz.service.impl;

import com.hxh.apboa.workflowbiz.vo.NodeMetadata;
import org.springframework.stereotype.Service;
import com.hxh.apboa.workflowbiz.service.WorkflowMetadataService;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowMetadataServiceImpl implements WorkflowMetadataService {
    @Override
    public List<NodeMetadata> nodeMetadata() {
        return List.of(
                node("START", "Start", "basic", "Workflow entry and request parameter mapping.", Map.of("method", "POST", "params", List.of()), true),
                node("END", "End", "basic", "Workflow response renderer.", Map.of("formatterType", "JACKSON", "responseTemplate", "${input}"), false),
                node("IF_ELSE", "Condition", "logic", "Boolean branch by simple condition or expression.", Map.of("evaluatorType", "GROOVY"), true),
                node("NON_EMPTY_SELECT", "Non Empty Select", "logic", "Route to the first non-empty input.", Map.of("strategy", "FIRST"), true),
                node("MATCH_RESULT", "Match Result", "logic", "Route by equals or contains match.", Map.of("matchType", "EQUALS", "caseSensitive", true), true),
                node("LOOP", "Loop", "logic", "Repeat a sub workflow until max iterations or termination expression.", Map.of("loopVariable", "loopIndex", "maxIterations", 1000, "itemVariable", "item"), false),
                node("ITERATE", "Iterate", "logic", "Run custom iterator code against an array input.", Map.of("language", "JAVA"), false),
                node("AGENT", "Agent", "ai", "Call a blocking ReAct agent with model, prompt, skills, tools, and MCP.", Map.of("formatterType", "STRING", "modelParamsOverrideEnabled", false, "userPrompt", "", "skillPackageIds", List.of(), "toolIds", List.of(), "mcps", List.of(), "maxIterations", 5, "structuredOutputEnabled", false), false),
                node("HTTP_EXTERNAL", "HTTP Request", "integration", "Call external HTTP APIs with retry and templated parameters.", Map.of("formatterType", "STRING", "connectTimeout", 10, "readTimeout", 30, "writeTimeout", 30, "maxRetries", 3, "followRedirects", true, "syncExecute", true, "bodyToObject", true), false),
                node("CODE", "Code", "integration", "Execute custom Groovy code.", Map.of("language", "JAVA"), false),
                node("DB_SELECT", "DB Select", "data", "Run a parameterized SELECT.", Map.of("formatterType", "VELOCITY", "params", List.of()), false),
                node("DB_INSERT", "DB Insert", "data", "Run a parameterized INSERT.", Map.of("formatterType", "VELOCITY", "params", List.of()), false),
                node("DB_UPDATE", "DB Update", "data", "Run a parameterized UPDATE.", Map.of("formatterType", "VELOCITY", "params", List.of()), false),
                node("DB_DELETE", "DB Delete", "data", "Run a parameterized DELETE.", Map.of("formatterType", "VELOCITY", "params", List.of()), false),
                node("CACHE_FETCH", "Cache Fetch", "data", "Read Redis value.", Map.of("formatterType", "VELOCITY"), false),
                node("CACHE_SET", "Cache Set", "data", "Write Redis value.", Map.of("formatterType", "VELOCITY", "expire", 0), false),
                node("CACHE_REMOVE", "Cache Remove", "data", "Delete Redis key.", Map.of("formatterType", "VELOCITY"), false),
                node("CACHE_REFRESH", "Cache Refresh", "data", "Refresh Redis key TTL.", Map.of("formatterType", "VELOCITY", "expire", 3600), false),
                node("MQ_PUSH", "MQ Push", "message", "Push a message to Kafka, RabbitMQ, or RocketMQ.", Map.of("templateType", "STRING"), false),
                node("VARIABLE_AGG", "Variable Aggregate", "transform", "Aggregate inputs into map, array, or string.", Map.of("strategy", "MAP", "excludeNull", false, "splicingSymbol", ""), false),
                node("STRING_TEMPLATE", "String Template", "transform", "Render a string template.", Map.of("templateType", "STRING", "template", ""), false),
                node("STRING_SPLIT", "String Split", "transform", "Split string into array.", Map.of("trimParts", true, "removeEmpty", true, "limit", -1, "maxResults", -1, "processingResult", true), false),
                node("LIST_FILTER", "List Filter", "list", "Filter array items.", Map.of("evaluatorType", "GROOVY"), false),
                node("LIST_SORT", "List Sort", "list", "Sort array items.", Map.of("evaluatorType", "GROOVY", "direction", "ASC", "nullFirst", false, "strictMode", false), false),
                node("SERIALIZE", "Serialize", "transform", "Serialize object to JSON, YAML, XML, Base64, or URL encoded.", Map.of("format", "JSON", "excludeNulls", false, "excludeEmptyStrings", false), false),
                node("UNSERIALIZE", "Unserialize", "transform", "Parse JSON, YAML, XML, Base64, or URL encoded input.", Map.of("format", "JSON", "excludeNulls", false), false)
        );
    }

    private NodeMetadata node(String type, String title, String group, String description, Map<String, Object> config, boolean branchable) {
        return new NodeMetadata(type, title, group, description, config, List.of("output"), branchable);
    }
}
