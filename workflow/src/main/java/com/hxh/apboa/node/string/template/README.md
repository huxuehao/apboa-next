该节点为字符串模版节点。
该节点的作用是将字符串中插入的变量，将变量替换成对应的值，最后输出替换后的字符串。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "字符串模板",
  "type": "STRING_TEMPLATE",
  "config": {
    "templateType": "STRING", // STRING | VELOCITY
    "template": "我是${input02}，我今年${input01}岁了"
  },
  "inputConfigs": [
    {
      "name": "input01",
      "type": "String",
      "value": 1000,
      "classify": "CONSTANT",
      "sourceNodeId": "",
      "sourceOutputName": ""
    }, {
      "name": "input02",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "String"
    }
  ]
}
```
