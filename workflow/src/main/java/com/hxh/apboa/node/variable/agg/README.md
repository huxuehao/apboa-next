该节点为变量聚合节点，用于将多个输入聚合成一个输出，支持聚合成Map、Array、String。
注意：该节点的多个输入的数据值类型必须一致。

下面为该节点的配置文件为：
```json
{
  "id": "当前节点ID",
  "name": "变量聚合",
  "type": "VARIABLE_AGG",
  "config": {
    "strategy": "MAP",
    "splicingSymbol": "",
    "excludeNull": false
  },
  "inputConfigs": [
    {
      "name": "name1",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "sourceNodeId输出名称"
    },{
      "name": "name2",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "sourceNodeId输出名称"
    },{
      "name": "name3",
      "type": "String",
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
