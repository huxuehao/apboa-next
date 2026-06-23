package com.hxh.apboa.node.loop;

import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.workflow.core.Edge;
import com.hxh.apboa.workflow.core.Workflow;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：循环节点配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {

    /**
     * 循环变量名，在子工作流中可通过该变量名获取当前迭代索引
     */
    private String loopVariable = "loopIndex";

    /**
     * 最大迭代次数，防止死循环
     */
    private int maxIterations = 1000;

    /**
     * 循环终止条件表达式（Groovy 表达式）
     * 当表达式求值为 true 时提前终止循环
     * 例如: "item == null" 或 "loopIndex >= 100"
     */
    private String terminationExpression;

    /**
     * 子工作流节点列表
     */
    private List<Node> subNodes;

    /**
     * 子工作流边列表
     */
    private List<Edge> subEdges;

    /**
     * 子工作流入口节点ID（必须是 subNodes 中的一个节点）
     */
    private String entryNodeId;

    /**
     * 迭代数据源变量名（来自上游节点输出或全局变量）
     * 如果为 null，则使用纯计数循环（0 到 maxIterations-1）
     * 如果非 null，则从变量上下文中获取该变量对应的集合进行迭代
     */
    private String iterateDataSource;

    /**
     * 迭代数据源中每个元素的变量名，在子工作流中可通过该变量名获取当前元素
     */
    private String itemVariable = "item";

    /**
     * 持有 SubWorkflow 的工作流实例（用于执行子工作流）
     */
    private Workflow workflow;
}
