开始节点，作为一个流程的开始节点

该节点对应的 json 存储示例如下，注意：config.params[x].name的值不允许使用“_”，且不允许重复
```json
{
  "id": "当前节点ID",
  "name": "开始",
  "type": "START",
  "config": {
    "protocol": "HTTP",
    "method": "POST",
    "path": "/test/path",
    "contentType": "*",
    "params": [ // 参数列表，name 不允许使用“_”，且不允许重复
      {
        "position":"PATH",
        "name": "code",
        "type": "String",
        "required": true,
        "remark": "参数描述",
        "defaultValue": null
      },{
        "position":"QUERY",
        "name": "age",
        "type": "Integer",
        "required": false,
        "remark": "参数描述",
        "defaultValue": "18"
      },{
        "position":"HEADER",
        "name": "requestIp",
        "type": "String",
        "required": true,
        "remark": "请求IP",
        "defaultValue": null
      }
    ],
    "accessLimit": {
      "enable": true,
      "unit": "MINUTE",
      "ipTimes": 10000,
      "routerTimes": 10000
    },
    "authenticate": {
      "enable": true,
      "position": "HEADER",
      "name": "Authorization"
    }
  },
  "inputConfigs": [],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "body",
      "type": "Object"
    },
    {
      "fromNodeId": "当前节点ID",
      "name": "code",
      "type": "String"
    },
    {
      "fromNodeId": "当前节点ID",
      "name": "age",
      "type": "Integer"
    },
    {
      "fromNodeId": "当前节点ID",
      "name": "requestIp",
      "type": "String"
    }
  ]
}
```
