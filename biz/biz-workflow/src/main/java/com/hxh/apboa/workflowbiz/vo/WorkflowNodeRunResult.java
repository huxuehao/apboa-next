package com.hxh.apboa.workflowbiz.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 单节点独立运行结果。
 *
 * @author huxuehao
 */
@Getter
@Setter
public class WorkflowNodeRunResult {
    /**
     * 节点ID。
     */
    private String nodeId;
    /**
     * 节点名称。
     */
    private String nodeTitle;
    /**
     * 节点类型。
     */
    private String nodeType;
    /**
     * 执行状态：SUCCESS / FAIL / RUNNING。
     */
    private String status;
    /**
     * 默认输出。
     */
    private Object output;
    /**
     * 全部输出。
     */
    private Map<String, Object> outputs;
    /**
     * 解析后的输入。
     */
    private Map<String, Object> inputs;
    /**
     * 执行上下文（运行日志）。
     */
    private Map<String, Object> executionContext;
    /**
     * 校验错误（单节点运行时通常为空）。
     */
    private Map<String, Object> verifyErrors;
    /**
     * 错误信息。
     */
    private String error;
    /**
     * 开始时间（毫秒时间戳）。
     */
    private Long startTime;
    /**
     * 结束时间（毫秒时间戳）。
     */
    private Long endTime;
    /**
     * 耗时（毫秒）。
     */
    private Long duration;
}
