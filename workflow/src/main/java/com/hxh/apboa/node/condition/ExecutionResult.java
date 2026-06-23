package com.hxh.apboa.node.condition;

/**
 * 描述：分支执行结果
 *
 * @author huxuehao
 **/
public record ExecutionResult(String nextNodeId, boolean result) {
}
