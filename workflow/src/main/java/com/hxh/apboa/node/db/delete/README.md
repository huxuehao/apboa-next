该节点为数据库删除节点（db-delete）节点。

该节点根据配置的数据源ID从 apboa-datasource 模块获取数据源连接配置，使用 HikariCP 连接池执行 SQL 删除操作。支持 MySQL、Oracle、PostgreSQL 三种数据库类型。返回受影响的行数。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "数据库删除",
  "type": "DB_DELETE",
  "config": {
    "datasourceId": "数据源ID",
    "sql": "DELETE FROM users WHERE age < ?",
    "params": [
      {
        "value": "18",
        "type": "Integer"
      }
    ],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Integer"
    }
  ]
}
```
