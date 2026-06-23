该节点为 HTTP 外部请求节点（http-external）。基于 OkHttp 发起 HTTP 请求，支持同步/异步执行、重试机制、路径参数/查询参数/请求头变量替换，以及多种 Content-Type（JSON、FORM_URLENCODED、FORM_DATA 等）。

该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "Http节点",
  "type": "HTTP_EXTERNAL",
  "config": {
    "formatterType": "STRING",
    "connectTimeout": 10,
    "readTimeout": 30,
    "writeTimeout":30,
    "maxRetries": 3,
    "retryStatusCodes": [408, 429, 500],
    "followRedirects": true,
    "syncExecute": true,
    "bodyToObject": true,
    "request": {
      "url": "${requestUrl}",
      "method": "POST",
      "contentType": "JSON",
      "pathParams": [],
      "queryParams": [
        {
          "key": "name",
          "value": "${name}"
        }, {
          "key": "age",
          "value": "${age}"
        }
      ],
      "headers": [
        {
          "key": "Route-Lab-Auth",
          "value": "bearer ${auth}"
        }
      ],
      "body": null
    }
  },
  "inputConfigs": [
    {
      "name": "requestUrl",
      "type": "String",
      "classify": "CONSTANT",
      "value": "http://127.0.0.1:6688/test/routelab/http/node"
    },{
      "name": "name",
      "type": "String",
      "classify": "CONSTANT",
      "value": "寒江"
    },{
      "name": "auth",
      "type": "String",
      "classify": "CONSTANT",
      "value": "hsjkoojwekem345678@fguendk="
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Object"
    }
  ]
}
```
