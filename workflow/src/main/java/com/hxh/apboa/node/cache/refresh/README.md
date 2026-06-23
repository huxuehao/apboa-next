该节点为缓存刷新节点（cache-refresh）节点。

该节点根据配置的缓存ID从 apboa-cache 模块获取 Redis 连接配置，刷新指定 key 在 Redis 中的过期时间。返回操作是否成功。

key 支持 Velocity 动态变量语法，变量由 inputConfigs 传入框架后自动解析替换。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "缓存刷新",
  "type": "CACHE_REFRESH",
  "config": {
    "cacheId": "缓存ID",
    "key": "user:${userId}",
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
