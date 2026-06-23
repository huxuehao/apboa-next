该节点为消息推送节点（mq-push）节点。

该节点根据配置的 MQ ID从 apboa-mq 模块获取消息中间件连接配置，向指定的消息中间件推送消息。支持 Kafka、RabbitMQ、RocketMQ 三种消息中间件。

消息内容支持两种方式：
1. **静态消息（message）**：直接指定消息内容。
2. **模板消息（messageTemplate）**：支持变量替换的模板格式，如 `Hello, ${name}!`。模板类型通过 templateType 指定（STRING / JACKSON / VELOCITY），默认使用 STRING 模板引擎。

其中 `topicOrQueue`、`key`、`messageTemplate` 均支持 Velocity 动态变量语法，变量由 inputConfigs 传入框架后自动解析替换。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "消息推送",
  "type": "MQ_PUSH",
  "config": {
    "mqId": "MQ配置ID",
    "topicOrQueue": "${serviceName}-order-topic",
    "key": "${orderKey}",
    "message": "{\"orderId\": 1001, \"status\": \"paid\"}",
    "messageTemplate": "{\"orderId\": ${orderId}, \"status\": \"${status}\"}",
    "templateType": "VELOCITY"
  },
  "inputConfigs": [
    {
      "name": "orderId",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "源节点输出参数名称"
    },
    {
      "name": "status",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "status"
    },
    {
      "name": "serviceName",
      "type": "String",
      "classify": "CONSTANT",
      "value": "prod"
    },
    {
      "name": "orderKey",
      "type": "String",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "源节点ID",
      "sourceOutputName": "orderKey"
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
