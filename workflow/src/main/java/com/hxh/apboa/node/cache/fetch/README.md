该节点为缓存获取节点（cache-fetch）节点。

该节点根据配置的缓存ID从 apboa-cache 模块获取 Redis 连接配置，从 Redis 中获取指定 key 的值并返回。

key 支持 Velocity 动态变量语法，变量由 inputConfigs 传入框架后自动解析替换。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "缓存获取",
  "type": "CACHE_FETCH",
  "config": {
    "cacheId": "缓存ID",
    "key": "user:${userId}",
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [
    {
      "name": "userId",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
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
