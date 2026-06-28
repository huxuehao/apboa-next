# UNSERIALIZE

## Purpose
Parse serialized text into workflow data.

## JSON
```json
{"id":"parse-1","name":"Parse JSON","type":"UNSERIALIZE","config":{"format":"JSON","excludeNulls":false,"encoding":"UTF-8"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"parse-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| format | enum | yes | - | JSON, YAML, XML, BASE64, URL_ENCODED | select |
| excludeNulls | boolean | no | false | true/false | switch |
| encoding | string | no | UTF-8 | charset | input/select |

## Inputs
Default input name is `input`; it should resolve to serialized text or bytes.

## Outputs
Default output name is `output`. Runtime output is parsed object/string depending on format.

## Runtime
Parses input according to format and encoding.

## Failures
Fails on invalid serialized content, unsupported format, parser errors, or invalid encoding.

## Frontend Notes
Use a format select and show sample output in debug panel.
