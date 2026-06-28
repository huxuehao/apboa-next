# DB_SELECT

## Purpose
Execute a parameterized SELECT statement against an enabled datasource.

## JSON
```json
{
  "id": "db-select-1",
  "name": "Select users",
  "type": "DB_SELECT",
  "config": {
    "datasourceId": "1880000000000000002",
    "sql": "select * from user where id = ?",
    "params": [{ "value": "${input.userId}", "type": "LONG" }],
    "formatterType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "db-select-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| datasourceId | string | yes | - | enabled datasource id | resource select from `GET /api/datasource?enabled=1` |
| sql | string | yes | - | SQL with `?` placeholders | SQL editor |
| params | array | no | [] | ordered `DbParam` values | parameter grid |
| params[].value | string | yes | - | literal or template | input/template cell |
| params[].type | enum | yes | STRING | STRING, INTEGER, INT, LONG, DOUBLE, FLOAT, BOOLEAN, BOOL | select |
| formatterType | enum | no | VELOCITY | VELOCITY, STRING, JACKSON | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`. Use `input` for upstream data referenced by parameter templates.

## Outputs
The default output name is `output`. Runtime output is the result list or table-like structure returned by the DB executor.

## Runtime
The SQL text itself is not templated as a whole. Use `?` placeholders. Parameter values are rendered with `formatterType`, converted by `params[].type`, then bound in order.

## Failures
Fails when datasource is missing/disabled, SQL is blank, placeholder count does not match params, parameter conversion fails, or JDBC execution fails.

## Frontend Notes
Use a SQL editor, ordered parameter rows, datasource connection check, and a warning that SQL identifiers cannot be dynamically templated by the backend.
