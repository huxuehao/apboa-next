# CACHE_FETCH

## Purpose
Read one value from a configured Redis cache. Use it when a workflow needs reusable state, temporary results, idempotency keys, or values produced by other systems.

## JSON
```json
{
  "id": "cache-fetch-1",
  "name": "Fetch cache",
  "type": "CACHE_FETCH",
  "config": {
    "cacheId": "1880000000000000001",
    "key": "user:${input.userId}",
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "cache-fetch-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| cacheId | string | yes | - | enabled cache id | resource select from `GET /api/cache?enabled=1` |
| key | string | yes | - | template string | template input |
| formatterType | enum | no | VELOCITY | VELOCITY, STRING, JACKSON | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`. The default input name should be `input` when the key template references upstream data.

## Outputs
The default output name is `output`. Runtime value is the Redis value returned by the cache operator; missing keys return null.

## Runtime
The node loads `Cache` by `cacheId`, requires `Cache.enabled = 1`, renders `key` with the selected formatter, and reads the value from Redis.

## Failures
Fails when `cacheId` or `key` is blank, the cache resource does not exist or is disabled, Redis connection fails, or template rendering fails.

## Frontend Notes
Show a cache selector, a connection check action, and a template-aware key editor. Do not allow arbitrary resource ids when the enabled cache list is available.
