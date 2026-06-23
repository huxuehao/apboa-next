该节点为缓存设置节点（cache-set）节点。

该节点根据配置的缓存ID从 apboa-cache 模块获取 Redis 连接配置，向 Redis 中设置键值对。支持配置过期时间（秒）。

key 和 value 支持 Velocity 动态变量语法，变量由 inputConfigs 传入框架后自动解析替换。
value 可以是 JSON 对象、字符串或数字，若为复杂对象则会先序列化为 JSON 再进行模板解析。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "缓存设置",
  "type": "CACHE_SET",
  "config": {
    "cacheId": "缓存ID",
    "key": "user:${userId}",
    "value": {"name": "${userName}", "age": ${userAge}},
    "expire": 3600,
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [
    {
      "name": "userId",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
    },
    {
      "name": "userName",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "userName"
    },
    {
      "name": "userAge",
      "type": "Integer",
      "classify": "CONSTANT",
      "value": 25
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Boolean"
    }
  ]
}
```
