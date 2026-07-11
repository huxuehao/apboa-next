package com.hxh.apboa.node.base;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 描述：节点输出
 *
 * @author huxuehao
 **/
public class NodeOutput {
    /**
     * 节点ID
     */
    @Getter
    @Setter
    private String nodeId;
    /**
     * 节点名称
     */
    @Getter
    @Setter
    private String nodeName;
    /**
     * 节点类型
     */
    @Getter
    @Setter
    private NodeType nodeType;
    @Getter
    @Setter
    private Instant startTime;
    @Getter
    @Setter
    private Instant endTime;
    /**
     * 节点执行状态
     */
    @Getter
    @Setter
    private ExecutionStatus status;
    /**
     * 节点输出
     * 输出字段名 -> 值
     */
    private final Map<String, Object> outputs;
    /**
     * 节点执行时的上下文信息
     * key -> value
     * 节点执行过程中，节点执行器会向该字段中添加一些信息，例如执行过程中产生的一些元数据
     */
    @Getter
    private Map<String, Object> executionContext;
    /**
     * 节点验证结果Map,有问题则有值
     */
    @Getter
    private Map<String, Object> verifyErrors;
    /**
     * 节点执行错误信息
     */
    @Getter
    @Setter
    private String errorMessage;

    public enum ExecutionStatus {
        SUCCESS,  // 成功
        FAILED,   // 失败
        UN_VERIFY,// 配置校验不通过
        SKIPPED,  // 跳过
        RUNNING   // 运行中
    }

    public NodeOutput(String nodeId, String nodeName, NodeType nodeType) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.startTime = Instant.now();
        this.status = ExecutionStatus.RUNNING;
        this.outputs = new LinkedHashMap<>(); // 保持输出顺序
        this.executionContext = new HashMap<>();
        this.verifyErrors = new HashMap<>();
    }

    /**
     * 添加输出
     *
     * @param outputName 输出字段名
     * @param value 输出值
     */
    public void addOutput(String outputName, Object value) {
        this.outputs.put(outputName, value);
    }

    /**
     * 批量添加输出
     *
     * @param outputs 输出字段名和值
     */
    public void addOutputs(Map<String, Object> outputs) {
        this.outputs.putAll(outputs);
    }

    /**
     * 获取输出
     *
     * @param outputName 输出字段名
     * @return 输出值
     */
    public Object getOutput(String outputName) {
        return this.outputs.get(outputName);
    }
    /**
     * 获取默认输出
     *
     * @return 输出值
     */
    public Object getDefaultOutput() {
        return this.outputs.get(NodeConst.DEFAULT_OUTPUT_NAME);
    }

    /**
     * 获取所有输出
     *
     * @return 所有输出
     */
    public Map<String, Object> getAllOutput() {
        return this.outputs;
    }

    /**
     * 标记节点执行完成
     */
    public void markComplete() {
        this.endTime = Instant.now();
        this.status = ExecutionStatus.SUCCESS;
    }

    /**
     * 标记节点执行失败
     *
     * @param errorMessage 错误信息
     */
    public void markFailed(String errorMessage) {
        this.endTime = Instant.now();
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
    }
    /**
     * 标记节点校验失败
     *
     * @param verifyResult 校验结果
     */
    public void markUnverify(VerifyResult verifyResult) {
        this.endTime = Instant.now();
        this.status = ExecutionStatus.UN_VERIFY;
        verifyResult.getErrors().forEach((error) -> {
            verifyErrors.put(error.getField(), error.getMessage());
        });
    }

    /**
     * 计算节点执行时长
     *
     * @return 节点执行时长（毫秒）
     */
    public Long getExecutionDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toMillis();
        }
        return null;
    }

    /**
     * 检查是否包含某个输出字段
     *
     * @param outputName 输出字段名
     * @return 是否已经输出了指定字段
     */
    public boolean hasOutput(String outputName) {
        return outputs.containsKey(outputName);
    }

    /**
     * 获取所有输出字段名
     *
     * @return 所有输出字段名
     */
    public Set<String> getOutputNames() {
        return outputs.keySet();
    }

    /**
     * 添加执行上下文信息
     */
    public void addExecutionContext(String key, Object value) {
        this.executionContext.put(key, value);
    }
}
