# DB_INSERT

## Purpose
Execute a parameterized INSERT statement against an enabled datasource.

## JSON
```json
{
  "id": "db-insert-1",
  "name": "Insert audit",
  "type": "DB_INSERT",
  "config": {
    "datasourceId": "1880000000000000002",
    "sql": "insert into audit_log(user_id, content) values(?, ?)",
    "params": [
      { "value": "${input.userId}", "type": "LONG" },
      { "value": "${input.content}", "type": "STRING" }
    ],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "db-insert-1" }]
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
Accepts all input source types: `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, `EXPRESSION`. Default input name: `input`.

## Outputs
The default output name is `output`. Runtime output is the insert execution result, normally affected row count.

## Runtime
SQL is executed with prepared-statement placeholders. SQL text is not globally templated; only `params[].value` is rendered and converted.

## Failures
Fails on disabled/missing datasource, invalid SQL, parameter mismatch, conversion failure, constraint violations, or JDBC errors.

## Frontend Notes
Keep SQL and params visually linked. Provide datasource select and connection check. Do not allow frontend-only output names unless backend outputConfigs include them.
