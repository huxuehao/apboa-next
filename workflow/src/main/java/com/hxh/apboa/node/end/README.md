# END

## Purpose
Workflow terminal node. It formats the final response.

## JSON
```json
{"id":"end","name":"End","type":"END","config":{"responseTemplate":"${input}","formatterType":"JACKSON"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"end"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| responseTemplate | string | no | null | template string | template/code editor |
| formatterType | enum | no | JACKSON | JACKSON, VELOCITY, STRING | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`; default input name is `input`.

## Outputs
The default output name is `output`. Runtime output is the formatted response.

## Runtime
The node renders `responseTemplate` with the input/context and marks workflow termination.

## Failures
Fails when formatter rendering fails.

## Frontend Notes
Allow one or more END nodes. Highlight paths that do not reach an END.
