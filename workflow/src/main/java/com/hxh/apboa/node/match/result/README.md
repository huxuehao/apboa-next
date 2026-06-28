# MATCH_RESULT

## Purpose
Route by matching input against configured values.

## JSON
```json
{"id":"match-1","name":"Match result","type":"MATCH_RESULT","config":{"matches":[{"value":"OK","nextNodeId":"success"}],"matchType":"EQUALS","caseSensitive":true,"defaultNextNodeId":"fallback"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"match-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| matches | array | yes | [] | match rows | editable table |
| matches[].value | string | yes | - | compare value | input |
| matches[].nextNodeId | string | yes | - | node id | node select |
| matchType | enum | no | EQUALS | EQUALS and implementation enum values | select |
| caseSensitive | boolean | no | true | true/false | switch |
| defaultNextNodeId | string | no | null | node id | node select |

## Inputs
Default input name is `input`; all source types are accepted.

## Outputs
Default output name is `output`. Runtime output is the original or matched value depending on implementation.

## Runtime
Evaluates matches in order. If no match succeeds, uses `defaultNextNodeId` when configured.

## Failures
Fails on invalid match config, missing target node, unsupported match type, or input resolution error.

## Frontend Notes
Render one output handle per match plus optional default; keep edge targets synchronized with config.
