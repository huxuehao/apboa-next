该节点为字符串切割节点。

该节点对应的 json 存储示例如下：

```
{
  "id": "当前节点ID",
  "name": "字符串切割",
  "type": "STRING_SPLIT",
  "config": {
    "mode": "SIMPLE", // SIMPLE,REGEX,FIXED_LENGTH,LINE_BREAK,KEY_VALUE,MULTIPLE_DELIMITERS
    "keyValueDelimiter": "=", //键值对分隔符，mode为KEY_VALUE时
    "keyValueOutputFormat": "COLON_SEPARATED", // COLON_SEPARATED,EQUALS_SEPARATED,JSON_OBJECT,MAP_ENTRY,CUSTOM
    "keyValueCustomFormat":"", // 当keyValueOutputFormat为CUSTOM时填写，格式要求："%s===>%s"
    "delimiter": "分隔符", // 根据不同的模式填写不同的内容
    "trimParts": true, //是否去除空字符,
    "removeEmpty": true, //是否去除空元素,
    "limit": -1,
    "maxResults": -1,
    "processingResult": true,
    "prefix": "",
    "suffix": ""
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "String",
      "value": "常量字符串",
      "classify": "CONSTANT",
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
