该节点为代码执行节点（code）节点。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "代码执行",
  "type": "CODE",
  "config": {
    "codeSource": "待执行的代码字符串",
    "language": "JAVA"
  },
  "inputConfigs": [
    {
      "name": "arg1",
      "type": "String",
      "classify": "CONSTANT",
      "value": "常量值"
    },
    {
      "name": "arg2",
      "type": "Integer",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "res1",
      "type": "Object"
    },{
      "fromNodeId": "当前节点ID",
      "name": "res2",
      "type": "Object"
    }
  ]
}
```
