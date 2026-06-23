该节点为非空选择节点，该节点会根据配置的策略，选择一个非空的输入作为输出。

下面为该节点的配置文件为：
```json
{
  "id": "当前节点ID",
  "name": "非空选择",
  "type": "NON_EMPTY_SELECT",
  "config": {
    "strategy": "FIRST",
    "defaultNextNodeId": "默认节点ID"
  },
  "inputConfigs": [
    {
      "name": "name1",
      "type": "Object",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "sourceNodeId输出名称"
    },{
      "name": "name2",
      "type": "Object",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "sourceNodeId输出名称"
    },{
      "name": "name3",
      "type": "Object",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "sourceNodeId输出名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Object"
    }
  ]
}
```
