# NON_EMPTY_SELECT

## Purpose
Select the first or configured non-empty input and route forward.

## JSON
```json
{"id":"non-empty-1","name":"Non empty select","type":"NON_EMPTY_SELECT","config":{"strategy":"FIRST","defaultNextNodeId":"fallback"},"inputConfigs":[{"name":"a","sourceType":"NODE_OUTPUT","nodeId":"n1","outputName":"output"},{"name":"b","sourceType":"NODE_OUTPUT","nodeId":"n2","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"non-empty-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| strategy | enum | no | FIRST | FIRST and implementation enum values | select |
| defaultNextNodeId | string | no | null | node id | node select |

## Inputs
Supports multiple named inputs from all source types.

## Outputs
Default output name is `output`. Runtime output is the selected non-empty value.

## Runtime
Checks inputs according to `strategy`. If none match, routes to `defaultNextNodeId` when provided.

## Failures
Fails on invalid strategy, bad default node, or input resolution errors.

## Frontend Notes
Render multiple input rows and show fallback edge distinctly.
