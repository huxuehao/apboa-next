# DB_DELETE

## Purpose
Execute a parameterized DELETE statement against an enabled datasource.

## JSON
```json
{
  "id": "db-delete-1",
  "name": "Delete temp rows",
  "type": "DB_DELETE",
  "config": {
    "datasourceId": "1880000000000000002",
    "sql": "delete from temp_data where trace_id = ?",
    "params": [{ "value": "${input.traceId}", "type": "STRING" }],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "db-delete-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| datasourceId | string | yes | - | enabled datasource id | resource select |
| sql | string | yes | - | SQL with `?` placeholders | SQL editor |
| params | array | no | [] | ordered values | parameter grid |
| params[].value | string | yes | - | literal or template | input/template cell |
| params[].type | enum | yes | STRING | STRING, INTEGER, INT, LONG, DOUBLE, FLOAT, BOOLEAN, BOOL | select |
| formatterType | enum | no | VELOCITY | VELOCITY, STRING, JACKSON | select |

## Inputs
Accepts all supported input source types. Default input name is `input`.

## Outputs
The default output name is `output`. Runtime output is typically affected row count.

## Runtime
Uses prepared-statement placeholders. SQL text is not globally templated; only parameter values are template-rendered and type-converted.

## Failures
Fails on disabled/missing datasource, invalid SQL, parameter mismatch, conversion failure, or JDBC execution error.

## Frontend Notes
Require explicit confirmation or warning for DELETE without WHERE if the editor later adds SQL lint rules.
