# CODE

## Purpose
Execute custom code inside the workflow.

## JSON
```json
{"id":"code-1","name":"Code","type":"CODE","config":{"language":"JAVA","codeSource":"return input"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"code-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| codeSource | string | yes | - | code text | CodeMirror editor |
| language | enum | yes | - | JAVA, JAVASCRIPT; current loader uses JAVA for Groovy-backed execution | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, `EXPRESSION`; default input name is `input`.

## Outputs
The default output name is `output`. Runtime value is the script return value.

## Runtime
Compiles and executes code with access to resolved input and node context variables.

## Failures
Fails on missing code/language, compilation error, runtime exception, or unsupported language.

## Frontend Notes
Use CodeMirror, show available variables, and warn that code can fail the whole run.
