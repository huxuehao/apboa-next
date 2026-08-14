package com.hxh.apboa.workflowbiz.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 单节点独立运行请求。
 *
 * @author huxuehao
 */
@Getter
@Setter
public class WorkflowNodeRunRequest {
    /**
     * 完整节点定义（id、type、name、config、inputConfigs、outputConfigs）。
     */
    private JsonNode node;
    /**
     * 节点输入测试值，key 为输入名。
     * 仅针对原本为「节点输出」的输入，数组/对象类型请传 JSON 字符串。
     */
    private Map<String, Object> inputs;
    /**
     * 节点输入类型，key 为输入名，value 为 String/Long/Integer/Float/Double/Boolean/Array/Object。
     */
    private Map<String, String> inputTypes;
    /**
     * 全局变量（供「全局变量」和「表达式」类输入使用）。
     */
    private Map<String, Object> variables;
}
