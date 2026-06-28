# IF_ELSE

## Purpose
Boolean branch node. It chooses true or false next node.

## JSON
```json
{"id":"if-1","name":"Condition","type":"IF_ELSE","config":{"evaluatorType":"GROOVY","conditionExpression":"input.score > 60","trueNextNodeId":"pass","falseNextNodeId":"fail"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"if-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| evaluatorType | string | no | GROOVY | GROOVY | select |
| conditionExpression | string | conditional | - | expression | code editor |
| allowInputType | array | no | null | output variable types | multi-select |
| scope | enum | conditional | - | config enum | select |
| inputIsNullUse | boolean | no | null | true/false | switch |
| symbol | enum | conditional | - | config enum | select |
| compareTo | object | conditional | - | compare target | form |
| trueNextNodeId | string | yes | - | node id | branch handle/edge |
| falseNextNodeId | string | yes | - | node id | branch handle/edge |

## Inputs
Accepts all supported source types; default input name is `input`.

## Outputs
Default output name is `output`. Branch handles should be `true` and `false`; runtime chooses next node by config.

## Runtime
Uses either expression evaluation or simple compare config, then routes to `trueNextNodeId` or `falseNextNodeId`.

## Failures
Fails on invalid expression, missing branch target, bad compare config, or unsupported evaluator.

## Frontend Notes
Render two outgoing handles labelled true/false and keep config targets synchronized with edges.
