# ITERATE

## Purpose
Execute custom iterator code over an input collection.

## JSON
```json
{
  "id": "iterate-1",
  "name": "Iterate",
  "type": "ITERATE",
  "config": {
    "iterateCode": "return input.collect { it.name }",
    "language": "JAVA"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "iterate-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| iterateCode | string | yes | - | executable code | code editor |
| language | enum | yes | - | JAVA, JAVASCRIPT; current loader uses JAVA for Groovy-backed execution | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`; default input name is `input`.

## Outputs
The default output name is `output`. Runtime value is whatever the iterator code returns.

## Runtime
The node evaluates `iterateCode` using the selected code language and the resolved input/context variables. Unlike `LOOP`, this node does not model sub-flow nodes or edges.

## Failures
Fails on missing code/language, script compilation errors, runtime exceptions, or unsupported language.

## Frontend Notes
Use the CodeMirror editor for `iterateCode`, show available variables, and keep output name fixed to `output`.
