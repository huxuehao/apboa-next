# VARIABLE_AGG

## Purpose
Aggregate multiple inputs into a map, list, or joined string.

## JSON
```json
{"id":"agg-1","name":"Aggregate","type":"VARIABLE_AGG","config":{"strategy":"MAP","excludeNull":false,"splicingSymbol":","},"inputConfigs":[{"name":"a","sourceType":"NODE_OUTPUT","nodeId":"n1","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"agg-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| strategy | enum | no | MAP | MAP, LIST, STRING | segmented select |
| excludeNull | boolean | no | false | true/false | switch |
| splicingSymbol | string | no | empty | delimiter for STRING | input |

## Inputs
Supports all source types and multiple named inputs. Names become map keys when `strategy=MAP`.

## Outputs
Default output name is `output`. Runtime value follows the aggregation strategy.

## Runtime
Resolves all inputConfigs, optionally drops nulls, then aggregates.

## Failures
Fails on invalid strategy or input resolution errors.

## Frontend Notes
Provide add/remove input rows and keep `output` as the primary output.
