# STRING_SPLIT

## Purpose
Split a string into list or key-value output.

## JSON
```json
{"id":"split-1","name":"Split","type":"STRING_SPLIT","config":{"mode":"SINGLE_DELIMITER","delimiter":",","trimParts":true,"removeEmpty":true,"limit":-1,"maxResults":-1,"processingResult":true},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"split-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| mode | enum | yes | - | implementation enum | select |
| delimiter | string | conditional | - | delimiter | input |
| delimiters | array | conditional | - | multiple delimiters | tag input |
| trimParts | boolean | no | true | true/false | switch |
| removeEmpty | boolean | no | true | true/false | switch |
| limit | int | no | -1 | Java split limit | number input |
| maxResults | int | no | -1 | max output items | number input |
| processingResult | boolean | no | true | true/false | switch |
| prefix/suffix | string | no | null | key-value wrappers | input |
| keyValueDelimiter | string | no | = | delimiter | input |
| keyValueOutputFormat | enum | no | COLON_SEPARATED | implementation enum | select |
| keyValueCustomFormat | string | no | null | custom format | input |

## Inputs
Default input name is `input`; it should resolve to a string.

## Outputs
Default output name is `output`. Runtime output is split parts or formatted key-value data.

## Runtime
Applies selected split mode, trim/remove-empty rules, limits, and optional key-value formatting.

## Failures
Fails on missing required delimiter config, non-string input, invalid mode, or invalid limits.

## Frontend Notes
Show fields conditionally by mode to avoid invalid JSON.
