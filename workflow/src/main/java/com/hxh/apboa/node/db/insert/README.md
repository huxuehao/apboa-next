该节点为数据库插入节点（db-insert）节点。

该节点根据配置的数据源ID从 apboa-datasource 模块获取数据源连接配置，使用 HikariCP 连接池执行 SQL 插入操作。支持 MySQL、Oracle、PostgreSQL 三种数据库类型。返回受影响的行数。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "数据库插入",
  "type": "DB_INSERT",
  "config": {
    "datasourceId": "数据源ID",
    "sql": "INSERT INTO users (name, age) VALUES (?, ?)",
    "params": [
      {
        "value": "${userName}",
        "type": "String"
      },
      {
        "value": "${userAge}",
        "type": "Integer"
      }
    ],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [
    {
      "name": "userName",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
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
      "type": "Integer"
    }
  ]
}
```
