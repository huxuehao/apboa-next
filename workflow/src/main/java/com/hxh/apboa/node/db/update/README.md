该节点为数据库更新节点（db-update）节点。

该节点根据配置的数据源ID从 apboa-datasource 模块获取数据源连接配置，使用 HikariCP 连接池执行 SQL 更新操作。支持 MySQL、Oracle、PostgreSQL 三种数据库类型。返回受影响的行数。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "数据库更新",
  "type": "DB_UPDATE",
  "config": {
    "datasourceId": "数据源ID",
    "sql": "UPDATE users SET age = ? WHERE name = ?",
    "params": [
      {
        "value": "${newAge}",
        "type": "Integer"
      },
      {
        "value": "${userName}",
        "type": "String"
      }
    ],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [
    {
      "name": "newAge",
      "type": "Integer",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
    },
    {
      "name": "userName",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID2",
      "sourceOutputName": "源节点输出参数名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Integer"
    }
  ]
}
```
