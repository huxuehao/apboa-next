# CACHE_REFRESH

## Purpose
Refresh the TTL of an existing Redis key.

## JSON
```json
{
  "id": "cache-refresh-1",
  "name": "Refresh cache",
  "type": "CACHE_REFRESH",
  "config": {
    "cacheId": "1880000000000000001",
    "key": "workflow:${input.id}",
    "expire": 3600,
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "cache-refresh-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| cacheId | string | yes | - | enabled cache id | resource select |
| key | string | yes | - | template string | template input |
| expire | long | yes | - | seconds | number input |
| formatterType | enum | no | VELOCITY | VELOCITY, STRING, JACKSON | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`; default input name should be `input`.

## Outputs
The default output name is `output`. Runtime output is the expire/refresh operation result.

## Runtime
Requires `Cache.enabled = 1`, renders `key`, and updates TTL to `expire` seconds.

## Failures
Fails when the cache resource is absent or disabled, `key` or `expire` is missing, Redis fails, or template rendering fails.

## Frontend Notes
Use a numeric TTL control in seconds and provide a resource selector plus connection check.
