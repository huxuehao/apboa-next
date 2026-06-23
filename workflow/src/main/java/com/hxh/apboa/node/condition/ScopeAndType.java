package com.hxh.apboa.node.condition;

import com.hxh.apboa.node.base.inputout.OutputConfig;

import java.util.Objects;

/**
 * 描述：条件分支计算范围 与 允许的输入值类型
 *
 * @author huxuehao
 **/
public class ScopeAndType {
    private final OutputConfig.VariableType type;
    private final Config.Scope scope;

    public ScopeAndType(OutputConfig.VariableType type, Config.Scope scope) {
        this.type = type;
        this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ScopeAndType that = (ScopeAndType) o;
        return scope == that.scope && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, type);
    }
}
