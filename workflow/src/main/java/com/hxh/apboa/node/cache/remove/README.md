# CACHE_REMOVE

## Purpose
Delete one Redis key from a configured cache resource.

## JSON
```json
{
  "id": "cache-remove-1",
  "name": "Remove cache",
  "type": "CACHE_REMOVE",
  "config": {
    "cacheId": "1880000000000000001",
    "key": "workflow:${input.id}",
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "cache-remove-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| cacheId | string | yes | - | enabled cache id | resource select |
| key | string | yes | - | template string | template input |
| formatterType | enum | no | VELOCITY | VELOCITY, STRING, JACKSON | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`. Use `input` when the key references upstream data.

## Outputs
The default output name is `output`. Runtime output is the remove operation result.

## Runtime
Requires `Cache.enabled = 1`, renders `key`, and removes it from Redis.

## Failures
Fails on blank `cacheId` or `key`, missing/disabled resource, Redis errors, or formatter errors.

## Frontend Notes
Warn that deletion is immediate. Provide resource check and key preview when variables are known.
