# LOOP

## Purpose
Run a sub-workflow repeatedly until max iterations is reached or a termination expression evaluates to true.

## JSON
```json
{
  "id": "loop-1",
  "name": "Loop items",
  "type": "LOOP",
  "config": {
    "loopVariable": "loopIndex",
    "maxIterations": 1000,
    "terminationExpression": "loopIndex >= 10",
    "subNodes": [],
    "subEdges": [],
    "entryNodeId": "sub-start",
    "iterateDataSource": "${input.items}",
    "itemVariable": "item"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "loop-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| loopVariable | string | no | loopIndex | variable name | input |
| maxIterations | int | no | 1000 | positive integer | number input |
| terminationExpression | string | no | null | expression | code editor |
| subNodes | array | yes | [] | node definitions | nested workflow editor |
| subEdges | array | yes | [] | edge definitions | nested workflow editor |
| entryNodeId | string | yes | - | id in subNodes | select |
| iterateDataSource | string | no | null | expression/template | input |
| itemVariable | string | no | item | variable name | input |

## Inputs
Accepts all supported input source types. Default input name is `input`.

## Outputs
The default output name is `output`. Runtime value contains loop execution results produced by the loop node.

## Runtime
Each iteration writes `loopVariable` into context. When `iterateDataSource` resolves to a list/array, the current item is written to `itemVariable`. The sub-flow starts at `entryNodeId`. The loop stops at `maxIterations` or when `terminationExpression` is true.

## Failures
Fails when `entryNodeId` is missing, the sub-flow is invalid, max iterations is invalid, expression execution fails, or a sub-node fails.

## Frontend Notes
Use a nested canvas or drawer for `subNodes/subEdges`. Show a hard max-iteration warning and make `entryNodeId` selectable from sub-flow nodes.
