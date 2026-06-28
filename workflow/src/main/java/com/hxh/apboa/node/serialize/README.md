# SERIALIZE

## Purpose
Serialize input object to JSON, YAML, XML, Base64, or URL encoded text.

## JSON
```json
{"id":"serialize-1","name":"Serialize","type":"SERIALIZE","config":{"mode":"TO_STRING","format":"JSON","excludeNulls":false,"excludeEmptyStrings":false,"encoding":"UTF-8"},"inputConfigs":[{"name":"input","sourceType":"NODE_OUTPUT","nodeId":"start","outputName":"output"}],"outputConfigs":[{"name":"output","fromNodeId":"serialize-1"}]}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| mode | enum | yes | - | implementation enum | select |
| format | enum | yes | - | JSON, YAML, XML, BASE64, URL_ENCODED | select |
| excludeNulls | boolean | no | false | true/false | switch |
| excludeEmptyStrings | boolean | no | false | true/false | switch |
| encoding | string | no | UTF-8 | charset | input/select |

## Inputs
Accepts all supported sources; default input name is `input`.

## Outputs
Default output name is `output`. Runtime output is serialized text or bytes depending on format.

## Runtime
Serializes resolved input according to `mode` and `format`.

## Failures
Fails on unsupported format, serialization errors, or invalid encoding.

## Frontend Notes
Use selects for mode/format and switches for exclusion flags.
