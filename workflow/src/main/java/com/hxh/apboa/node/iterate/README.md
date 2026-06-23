该节点为迭代（iterate）节点。该节点是用来迭代一个集合，并返回一个结果。迭代集合的逻辑是通过配置的代码字符串来实现的。

节点的配置如下：
```json
{
  "id": "当前节点ID",
  "name": "迭代",
  "type": "ITERATE",
  "config": {
    "iterateCode": "迭代元素所使用的代码字符串",
    "language": "JAVA"
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "Array",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点输出名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Array"
    }
  ]
}
```
