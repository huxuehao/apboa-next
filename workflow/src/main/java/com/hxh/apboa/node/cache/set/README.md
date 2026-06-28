# CACHE_SET

## Purpose
Write one value into a configured Redis cache. Use it for workflow state, intermediate data, or short-lived coordination values.

## JSON
```json
{
  "id": "cache-set-1",
  "name": "Set cache",
  "type": "CACHE_SET",
  "config": {
    "cacheId": "1880000000000000001",
    "key": "workflow:${input.id}",
    "value": "${input}",
    "expire": 3600,
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "cache-set-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| cacheId | string | yes | - | enabled cache id | resource select |
| key | string | yes | - | template string | template input |
| value | any | yes | - | literal or template value | JSON/template editor |
| expire | long | no | null | seconds; null or 0 means no expiration | number input |
| formatterType | enum | no | VELOCITY | VELOCITY, STRING, JACKSON | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`. Use `input` as the default input name.

## Outputs
The default output name is `output`. Runtime output is the cache operator result, normally a boolean-like success value.

## Runtime
Requires an enabled cache resource. `key` and templated `value` are rendered before writing. `expire = null` or `expire = 0` means the key is stored without TTL.

## Failures
Fails when required fields are missing, the cache is disabled or absent, Redis write fails, or template rendering fails.

## Frontend Notes
Expose TTL as seconds with an explicit "no expiration" state. Use enabled resource lists and provide `POST /api/cache/check/connect`.
