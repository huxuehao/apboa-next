# LIST_SORT

## Purpose
Sort list items by expression-derived key.

## JSON
```json
{"id":"sort-1","name":"Sort","type":"LIST_SORT","config":{"evaluatorType":"GROOVY","condition":"item.score","direction":"DESC","nullFirst":false,"strictMode":false},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"sort-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| evaluatorType | string | no | GROOVY | GROOVY | select |
| condition | string | yes | - | sort key expression | code editor |
| direction | enum | no | ASC | ASC, DESC | segmented control |
| nullFirst | boolean | no | false | true/false | switch |
| strictMode | boolean | no | false | true/false | switch |

## Inputs
Default input name is `input`; it should resolve to a list/array. All source types are accepted.

## Outputs
Default output name is `output`. Runtime output is the sorted list.

## Runtime
Evaluates `condition` per item and sorts by the resulting key.

## Failures
Fails on non-list input, expression error, incomparable values in strict mode, or unsupported direction.

## Frontend Notes
Provide ASC/DESC segmented control and a compact expression editor.
