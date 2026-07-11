package com.hxh.apboa.node.toolexecute;

import java.util.Map;

/**
 * 工具节点执行器桥接接口。
 * 由 engine 模块实现，避免 workflow 模块直接依赖 engine 造成循环依赖。
 *
 * @author huxuehao
 */
public interface ToolNodeExecutor {
    /**
     * 执行指定工具。
     *
     * @param toolId 工具ID
     * @param params 工具执行参数（key为参数名，value为参数值）
     * @return 工具执行结果
     */
    Object execute(Long toolId, Map<String, Object> params);
}
