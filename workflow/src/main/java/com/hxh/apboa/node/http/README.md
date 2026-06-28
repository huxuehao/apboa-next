# HTTP_EXTERNAL

## Purpose
Call an external HTTP API from the workflow.

## JSON
```json
{
  "id": "http-1",
  "name": "Call API",
  "type": "HTTP_EXTERNAL",
  "config": {
    "formatterType": "STRING",
    "connectTimeout": 10,
    "readTimeout": 30,
    "writeTimeout": 30,
    "maxRetries": 3,
    "retryStatusCodes": [429, 500, 502, 503],
    "followRedirects": true,
    "syncExecute": true,
    "bodyToObject": true,
    "request": {
      "method": "POST",
      "url": "https://api.example.com/tasks",
      "headers": { "Content-Type": "application/json" },
      "queryParams": {},
      "body": "{\"id\":\"${input.id}\"}"
    }
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "http-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| formatterType | enum | no | STRING | STRING, VELOCITY, JACKSON | select |
| connectTimeout/readTimeout/writeTimeout | int | no | 10/30/30 | seconds | number inputs |
| maxRetries | int | no | 3 | >= 0 | number input |
| retryStatusCodes | array | no | [] | HTTP status codes | tag number input |
| followRedirects | boolean | no | true | true/false | switch |
| syncExecute | boolean | no | true | true/false | switch |
| bodyToObject | boolean | no | true | true/false | switch |
| request.method | string | yes | - | GET/POST/PUT/PATCH/DELETE... | select |
| request.url | string | yes | - | template string | URL input |
| request.headers/queryParams | object | no | {} | string map | key-value editor |
| request.body | string | no | null | string only in current code | code textarea |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`; default input name is `input`.

## Outputs
The default output name is `output`. If `syncExecute=false`, output is the fixed string `ASYNC_EXECUTE`. If synchronous and `bodyToObject=true`, the response body is parsed as `JsonNode`; otherwise the raw response string is returned.

## Runtime
URL, headers, query parameters, and body string may use formatter templates. The current implementation accepts JSON body as a string; do not store body as an object unless backend support is added.

## Failures
Fails on invalid URL/method, timeout, retry exhaustion, HTTP client errors, or JSON parsing errors when `bodyToObject=true`.

## Frontend Notes
Use separate tabs for request, retry, and response parsing. The body editor should serialize JSON to a string before saving.
