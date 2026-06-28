# MQ_PUSH

## Purpose
Publish one message to Kafka, RabbitMQ, or RocketMQ through an enabled MQ resource.

## JSON
```json
{
  "id": "mq-push-1",
  "name": "Publish event",
  "type": "MQ_PUSH",
  "config": {
    "mqId": "1880000000000000003",
    "topicOrQueue": "workflow-events",
    "key": "${input.id}",
    "message": null,
    "messageTemplate": "{\"id\":\"${input.id}\"}",
    "templateType": "VELOCITY"
  },
  "inputConfigs": [{ "name": "input", "sourceType": "NODE_OUTPUT", "nodeId": "start", "outputName": "output" }],
  "outputConfigs": [{ "name": "output", "fromNodeId": "mq-push-1" }]
}
```

## Config
| Field | Type | Required | Default | Values | Frontend control |
| --- | --- | --- | --- | --- | --- |
| mqId | string | yes | - | enabled MQ id | resource select from `GET /api/mq?enabled=1` |
| topicOrQueue | string | yes | - | template string | input |
| key | string | no | null | template string | input |
| message | string | no | null | literal message | textarea |
| messageTemplate | string | no | null | template string | template/code editor |
| templateType | enum | no | STRING | STRING, VELOCITY, JACKSON | select |

## Inputs
Accepts `CONSTANT`, `VARIABLE`, `NODE_OUTPUT`, and `EXPRESSION`. Use `input` for template context.

## Outputs
The default output name is `output`. Runtime output is the publish result from the MQ operator.

## Runtime
Requires `Mq.enabled = 1`. `message` has priority when it is not blank; otherwise `messageTemplate` is rendered with `templateType`. `topicOrQueue`, `key`, and `messageTemplate` are template-rendered. For Kafka and RocketMQ, `topicOrQueue` means topic; for RabbitMQ it means queue or routing target according to the MQ resource config.

## Failures
Fails on missing/disabled MQ resource, blank destination, missing message content, template failure, or publish failure.

## Frontend Notes
Show resource type after selection. Label destination as "Topic / Queue" and expose a connection check button.
