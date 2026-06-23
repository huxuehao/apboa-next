该节点为结果匹配节点节点。类似于switch-case，其目的是返回下一个执行节点。

MatchType为EQUALS时，会将输入的内容进行toString()转成字符串，然后在与被匹配值进行比配。
MatchType为CONTAINS时，
   如果类型输入的类型为String，会将内容直接与被匹配值进行比配。
   如果类型输入的类型为Array，则将输入的子元素内容进行toString()转成字符串，然后与被匹配值进行比配。

如果匹配boolean类型的，caseSensitive必须设置为false，以适配大小写true/false

节点的配置如下：
```json
{
  "id": "当前节点ID",
  "name": "结果匹配",
  "type": "MATCH_RESULT",
  "config": {
    "matchType": "EQUALS", // EQUALS、CONTAINS
    "caseSensitive": "true",
    "defaultNextNodeId":"默认下一个节点ID",
    "matches": [
      {
        "matchValue": "匹配值",
        "nextNodeId": "匹配成功时，下一个节点ID"
      },
      {
        "matchValue": "匹配值",
        "nextNodeId": "匹配成功时，下一个节点ID"
      }, {
        "matchValue": "匹配值",
        "nextNodeId": "匹配成功时，下一个节点ID"
      }
    ]
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "String", // EQUALS支持 String、Integer、Long、Float、Double、Boolean；CONTAINS支持 String、Array
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点输出名称"
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
