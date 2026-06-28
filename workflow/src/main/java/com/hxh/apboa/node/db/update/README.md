# DB_UPDATE

## Purpose
Execute a parameterized UPDATE statement against an enabled datasource.

## JSON
```json
{
  "id": "db-update-1",
  "name": "Update status",
  "type": "DB_UPDATE",
  "config": {
    "datasourceId": "1880000000000000002",
    "sql": "update task set status = ? where id = ?",
    "params": [
      { "value": "${input.status}", "type": "STRING" },
      { "value": "${input.id}", "type": "LONG" }
    ],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "db-update-1" }]
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
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, `EXPRESSION`; default input name is `input`.

## Outputs
The default output name is `output`. Runtime output is typically affected row count.

## Runtime
The node binds params to `?` placeholders in order. SQL text is not whole-template-rendered; parameter values are rendered and converted.

## Failures
Fails for missing/disabled datasource, invalid SQL, bad parameter count/type, transaction/JDBC errors.

## Frontend Notes
Show affected row count in debug output. Use a warning for UPDATE without WHERE if SQL linting is added.
