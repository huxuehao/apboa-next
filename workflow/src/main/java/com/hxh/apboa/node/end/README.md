该节点为结束（http response）节点。

inputConfigs 支持多个输入，所有输入变量都会作为模板变量参与 responseTemplate 的解析。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "结束",
  "type": "END",
  "config": {
    "formatterType": "JACKSON",
    "responseTemplate": "响应结果模版，支持json、字符串、纯变量格式"
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "Object",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点输出名称"
    },
    {
      "name": "extra",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点输出名称"
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
