该节点为循环节点（loop）节点。

该节点支持两种循环模式：
1. **数据集合迭代循环**：基于上游节点输出的数据集合进行遍历，每次迭代处理集合中的一个元素。
2. **纯计数循环**：当未配置迭代数据源时，执行固定次数的纯计数循环。

循环体内是一个子工作流（sub-workflow），每次迭代都会从入口节点开始执行子工作流中的节点。循环节点会将当前迭代索引（loopVariable）和当前迭代元素（itemVariable）注入到上下文变量中，子工作流中的节点可以通过这些变量获取循环状态。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "循环节点",
  "type": "LOOP",
  "config": {
    "loopVariable": "loopIndex",
    "maxIterations": 1000,
    "terminationExpression": "item == null",
    "iterateDataSource": "dataList",
    "itemVariable": "item",
    "subNodes": [],
    "subEdges": [],
    "entryNodeId": "子工作流入口节点ID",
    "workflow": "工作流实例"
  },
  "inputConfigs": [
    {
      "name": "dataList",
      "type": "List",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "List"
    },
    {
      "fromNodeId": "当前节点ID",
      "name": "totalIterations",
      "type": "Integer"
    }
  ]
}
```
