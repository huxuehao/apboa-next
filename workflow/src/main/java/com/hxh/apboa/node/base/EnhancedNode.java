package com.hxh.apboa.node.base;

import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.inputout.InputConfig;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 描述：增强的工作流节点基类
 * 支持输入输出配置
 *
 * @author huxuehao
 **/
public abstract class EnhancedNode implements Node {
    protected String id;
    protected String name;
    protected NodeType type;
    // 节点的入边ID集合
    private List<String> inEdgeIds;
    // 节点的出边ID集合
    private List<String> outEdgeIds;

    @Getter
    protected List<InputConfig> inputConfigs = new ArrayList<>();
    @Getter
    protected List<OutputConfig> outputConfigs = new ArrayList<>();

    public EnhancedNode(String id, String name, NodeType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    @Override
    public NodeOutput execute(NodeContext context) {
        // 重置下一个节点ID
        context.resetNextNodeId();
        // 初始化输出节点
        NodeOutput output = initOutput();
        // 解析节点输入
        Map<String, Object> inputs = resolveInputs(context);
        // 校验参数
        VerifyResult verifyResult = verifyConfig(inputs);
        if (!verifyResult.isValid()) {
            output.markUnverify(verifyResult);
            return output;
        }
        // 节点执行逻辑
        output = doExecute(inputs, output, context);
        // 保存节点输出
        storeOutputs(output, context);

        return output;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public NodeType getType() {
        return this.type;
    }

    /**
     * 具体的节点执行逻辑，由子类实现
     *
     * @param inputs 基于improveInputs中元素的name->value的映射
     * @param context 执行上下文
     */
    protected abstract NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context);

    /**
     * 初始化节点输出
     * 将会在 public NodeOutput execute(NodeContext context) 中被执行
     */
    private NodeOutput initOutput() {
        NodeOutput output = new NodeOutput(id, name, type);
        output.setStartTime(Instant.now());
        output.setStatus(NodeOutput.ExecutionStatus.RUNNING);
        return output;
    }

    /**
     * 解析所有输入配置
     *
     * @param context 执行上下文
     * @return 解析完成的节点输入，已经将占位变量进行了解析
     */
    protected Map<String, Object> resolveInputs(NodeContext context) {
        Map<String, Object> map = new HashMap<>();
        for (InputConfig config : inputConfigs) {
            Object value = context.getVariables().resolveInput(config);
            map.put(config.getName(), value);
        }
        return map;
    }

    /**
     * 存储节点输出
     *
     * @param output  节点输出
     * @param context 执行上下文
     */
    protected void storeOutputs(NodeOutput output, NodeContext context) {
        context.getVariables().storeNodeOutput(getId(), output);
    }

    /**
     * 添加输入配置 @TODO 该方法必须在节点被初始化的时候进行调用？
     *
     * @param config 输入配置
     */
    public void addInputConfig(InputConfig config) {
        Set<String> collect = inputConfigs.stream().map(InputConfig::getName).collect(Collectors.toSet());
        if (collect.contains(config.getName())) {
            throw new RuntimeException("输入配置名称重复：" + config.getName());
        }
        inputConfigs.add(config);
    }

    /**
     * 添加输出配置 @TODO 该方法必须在节点被初始化的时候进行调用？
     *
     * @param config 输出配置
     */
    public void addOutputConfig(OutputConfig config) {
        Set<String> collect = outputConfigs.stream().map(OutputConfig::getName).collect(Collectors.toSet());
        if (collect.contains(config.getName())) {
            throw new RuntimeException("输出配置名称重复：" + config.getName());
        }
        outputConfigs.add(config);
    }

    @Override
    public void addInEdgeId(String edgeId) {
        if (inEdgeIds == null) {
            inEdgeIds = new ArrayList<>();
        }
        inEdgeIds.add(edgeId);
    }

    @Override
    public void addOutEdgeId(String edgeId) {
        if (outEdgeIds == null) {
            outEdgeIds = new ArrayList<>();
        }
        outEdgeIds.add(edgeId);
    }

    @Override
    public List<String> getInEdgeIds() {
        if (inEdgeIds == null) {
            inEdgeIds = new ArrayList<>();
        }
        return inEdgeIds;
    }

    @Override
    public List<String> getOutEdgeIds() {
        if (outEdgeIds == null) {
            outEdgeIds = new ArrayList<>();
        }
        return outEdgeIds;
    }

    @Override
    public boolean isInEdge(String edgeId) {
        if (inEdgeIds != null) {
            return inEdgeIds.contains(edgeId);
        }
        return false;
    }

    @Override
    public boolean isOutEdge(String edgeId) {
        if (outEdgeIds != null) {
            return outEdgeIds.contains(edgeId);
        }
        return false;
    }
}
