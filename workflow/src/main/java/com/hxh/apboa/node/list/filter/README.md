# LIST_FILTER

## Purpose
Filter list items by simple condition or expression.

## JSON
```json
{"id":"filter-1","name":"Filter","type":"LIST_FILTER","config":{"mode":"SIMPLE","evaluatorType":"GROOVY","condition":"item.enabled == true"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"filter-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| mode | enum | yes | - | implementation enum | select |
| supportType | enum | conditional | - | implementation enum | select |
| itemIsNullUse | boolean | no | null | true/false | switch |
| simpleSymbol | enum | conditional | - | implementation enum | select |
| evaluatorType | string | no | GROOVY | GROOVY | select |
| condition | string | conditional | - | expression | code editor |
| compareTo | object | conditional | - | compare target | form |

## Inputs
Accepts supported input sources; default input name is `input` and should resolve to a list/array.

## Outputs
Default output name is `output`. Runtime output is the filtered list.

## Runtime
For each item, evaluates simple config or expression with item/input context.

## Failures
Fails on non-list input, invalid condition, unsupported mode, or expression runtime error.

## Frontend Notes
Use a mode switch. Show expression editor only for expression mode.
