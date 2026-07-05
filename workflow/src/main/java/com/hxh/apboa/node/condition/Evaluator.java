package com.hxh.apboa.node.condition;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.WorkflowUtils;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.inputout.InputConfig;
import com.hxh.apboa.node.base.inputout.OutputConfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 描述：计算判断
 *
 * @author huxuehao
 **/
public class Evaluator {

    public static boolean evaluate(Config config, Map<String, Object> inputs, InputConfig inputConfig, NodeContext context) {
        // 输入值
        Object inputValue = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        // 输入值是否为空
        if (FuncUtils.isEmpty(inputValue)) {
            return config.getInputIsNullUse();
        }



        // 比较值
        Object compareValue;
        CompareTo compareTo = config.getCompareTo();
        CompareTo.Type compareToType = compareTo.getType();
        if (compareToType == CompareTo.Type.CONSTANT) {
            compareValue = compareTo.getValue();
        } else if (compareToType == CompareTo.Type.VARIABLE) {
            // 获取目标节点输出
            NodeOutput nodeOutput = context.getVariables().getNodeOutputs().get(compareTo.getSourceNodeId());
            if (nodeOutput == null) {
                throw new RuntimeException("找不到"+compareTo.getSourceNodeId()+"对应的节点输出");
            }
            // 获取变量值
            compareValue = nodeOutput.getOutput(compareTo.getValue().toString());
        } else {
            throw new RuntimeException("未知的比较类型");
        }

        // 基于数值推断类型
        OutputConfig.VariableType type = WorkflowUtils.inferType(compareValue);
        Config.Scope scope = config.getScope();
        Config.Symbol symbol = config.getSymbol();
        // 验证类型是否支持运算符
        VariableTypeSupportSymbol.verifySupportSymbol(type, scope, symbol);

        switch (symbol) {
            case EQ:
                if (scope == Config.Scope.SELF) {
                    return NumberComparator.compare(inputValue, compareValue) == 0;
                } else {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).length() == ((Integer) compareValue);
                    } else if (type == OutputConfig.VariableType.Array){
                        return ((List<?>) inputValue).size() == ((Integer) compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                }
            case NE:
                if (scope == Config.Scope.SELF) {
                    return NumberComparator.compare(inputValue, compareValue) != 0;
                } else {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).length() != ((Integer) compareValue);
                    } else if (type == OutputConfig.VariableType.Array){
                        return ((List<?>) inputValue).size() != ((Integer) compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                }
            case GT:
                if (scope == Config.Scope.SELF) {
                    return NumberComparator.compare(inputValue, compareValue) > 0;
                } else {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).length() > ((Integer) compareValue);
                    } else if (type == OutputConfig.VariableType.Array) {
                        return ((List<?>) inputValue).size() > ((Integer) compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                }
            case LT:
                if (scope == Config.Scope.SELF) {
                    return NumberComparator.compare(inputValue, compareValue) < 0;
                } else {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).length() < ((Integer) compareValue);
                    } else if (type == OutputConfig.VariableType.Array){
                        return ((List<?>) inputValue).size() < ((Integer) compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                }
            case GE:
                if (scope == Config.Scope.SELF) {
                    return NumberComparator.compare(inputValue, compareValue) >= 0;
                } else {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).length() >= ((Integer) compareValue);
                    } else if (type == OutputConfig.VariableType.Array){
                        return ((List<?>) inputValue).size() >= ((Integer) compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                }
            case LE:
                if (scope == Config.Scope.SELF) {
                    return NumberComparator.compare(inputValue, compareValue) <= 0;
                } else {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).length() <= ((Integer) compareValue);
                    } else if (type == OutputConfig.VariableType.Array){
                        return ((List<?>) inputValue).size() <= ((Integer) compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                }
            case CONTAINS:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).contains(compareValue.toString());
                    } else if (type == OutputConfig.VariableType.Array){
                        return ((List<?>) inputValue).contains(compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }

            case NOT_CONTAINS:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.String) {
                        return !((String) inputValue).contains(compareValue.toString());
                    } else if (type == OutputConfig.VariableType.Array){
                        return !((List<?>) inputValue).contains(compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case IS_ALL:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.Array){
                        for (Object o : ((List<?>) inputValue)) {
                            if (!o.equals(compareValue)) {
                                return false;
                            }
                        }
                        return true;
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case STARTS_WITH:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).startsWith(compareValue.toString());
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case ENDS_WITH:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.String) {
                        return ((String) inputValue).endsWith(compareValue.toString());
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case EQUALS:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.String) {
                        return Objects.equals(inputValue, compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case NOT_EQUALS:
                if (scope == Config.Scope.SELF) {
                    if (type == OutputConfig.VariableType.String) {
                        return !Objects.equals(inputValue, compareValue);
                    } else {
                        throw new RuntimeException("未知的变量类型");
                    }
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case IS_TRUE:
                if (scope == Config.Scope.SELF) {
                    return (Boolean) inputValue;
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
            case IS_FALSE:
                if (scope == Config.Scope.SELF) {
                    return !(Boolean) inputValue;
                } else {
                    throw new RuntimeException("不支持的作用域类型：" +  scope);
                }
        }

        throw new RuntimeException("未知的运算符");
    }
}
