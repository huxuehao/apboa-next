该节点为序列化节点。支持 BASE64、JSON、URL_ENCODED、XML、YAML 五种序列化格式。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "序列化",
  "type": "SERIALIZE",
  "config": {
    "mode": "PRETTY",
    "format": "JSON",
    "excludeNulls": true,
    "excludeEmptyStrings": true,
    "encoding": "UTF-8"
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "Object",
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
