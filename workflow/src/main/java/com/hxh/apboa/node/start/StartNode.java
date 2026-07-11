package com.hxh.apboa.node.start;

import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.feature.StartableNode;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.common.util.JsonUtils;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 描述：开始节点
 * 开始节点中没有什么执行逻辑，其存在的意义是引导工作流往下执行，
 * 同时将路径参数、请求参数、请求body、请求头等作为变量进行存储。
 *
 * @author huxuehao
 **/
public class StartNode extends EnhancedNode implements StartableNode {
    @Getter
    private final Config config;

    public StartNode(String id, String name, Config config) {
        super(id, name, NodeType.START);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(NodeOutput output) {
        putOutput(output);

        output.markComplete();
        return output;
    }

    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed( getName() + "执行失败: " + e.getMessage());
        return output;
    }

    /**
     * 添加输出参数
     *
     * @param output 输出
     */
    private void putOutput(NodeOutput output) {
        // 将请求的参数添加到输出中
        safeParams().forEach(param ->
                output.addOutput(createOutputName(param), param.getValue())
        );
    }

    /**
     * 创建输出参数名称
     *
     * @param param 参数
     * @return 输出参数名称
     */
    private String createOutputName(Param param) {
        return param.getName();
    }

//    /**
//     * 创建输出参数值
//     *
//     * @param param 参数
//     * @return 转换后的参数值
//     */
//    private Object createOutputValue(Param  param) {
//        String value = param.getValue();
//        if (value == null || value.isBlank()) {
//            return null;
//        }
//        OutputConfig.VariableType type = param.getType() == null ? OutputConfig.VariableType.String : param.getType();
//        return switch (type) {
//            case String -> param.getValue();
//            case Long -> Long.valueOf(param.getValue().toString());
//            case Integer -> Integer.valueOf(param.getValue());
//            case Float -> Float.valueOf(param.getValue());
//            case Double -> Double.valueOf(param.getValue());
//            case Boolean -> Boolean.valueOf(param.getValue());
//            case Array, Object -> JsonUtils.parse(param.getValue());
//            default ->throw new IllegalArgumentException("不支持的参数类型:" + param.getType());
//        };
//    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        VerifyResult result = VerifyResult.valid();
        // 参数名称不允许包含 "_"
        for (Param param : safeParams()) {
            if (param.getName() == null || param.getName().isBlank()) {
                result.addError("params", "参数名称不能为空");
                continue;
            }
            if (param.getType() == null) {
                result.addError("params." + param.getName(), "参数类型不能为空：" + param.getName());
                continue;
            }
            if (param.getName().contains("_")) {
                result.addError("params", "参数名称不允许包含 '_'：" + param.getName());
            }
            if (Boolean.TRUE.equals(param.getRequired()) && param.getValue() == null) {
                result.addError("params." + param.getName(), "必填参数不能为空：" + param.getName());
            }
        }

        // 参数名称不允许重复
        if (safeParams().stream().map(Param::getName).filter(name -> name != null && !name.isBlank()).distinct().count()
                != safeParams().stream().map(Param::getName).filter(name -> name != null && !name.isBlank()).count()) {
            result.addError("params", "参数名称重复");
        }

        result.setValid(result.getErrors().isEmpty());
        return result;
    }

    private List<Param> safeParams() {
        return config == null || config.getParams() == null ? List.of() : config.getParams();
    }
}
