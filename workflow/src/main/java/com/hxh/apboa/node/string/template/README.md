# STRING_TEMPLATE

## Purpose
Render a string template from workflow input/context.

## JSON
```json
{"id":"template-1","name":"Template","type":"STRING_TEMPLATE","config":{"templateType":"VELOCITY","template":"Hello ${input.name}"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"template-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| templateType | enum | no | STRING | STRING, VELOCITY, JACKSON | select |
| template | string | yes | - | template text | template editor |

## Inputs
Accepts all source types; default input name is `input`.

## Outputs
Default output name is `output`. Runtime output is the rendered string/object depending on formatter behavior.

## Runtime
Renders `template` with selected formatter and current context.

## Failures
Fails on missing template or rendering error.

## Frontend Notes
Use a template editor and variable picker.
