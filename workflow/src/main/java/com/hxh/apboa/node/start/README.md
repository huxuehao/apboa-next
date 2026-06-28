# START

## Purpose
Workflow entry node，作为工作流执行的起始点，引导下游节点依次执行。
将配置的参数列表作为变量写入节点输出，供后续节点引用。

## JSON
```json
{
  "id": "start",
  "name": "Start",
  "type": "START",
  "config": {
    "params": [
      {
        "name": "userId",
        "value": "123",
        "type": "Long",
        "required": true,
        "remark": "用户ID"
      }
    ]
  },
  "inputConfigs": [],
  "outputConfigs": []
}
```

## Config
| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| params | Param[] | no | [] | 请求参数定义列表，每个参数会作为输出变量写入上下文 |

### Param
| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| name | string | yes | - | 参数名称，将作为输出变量名 |
| value | string | yes | - | 参数值（字符串形式，运行时按 type 转换） |
| type | VariableType | yes | - | 参数类型，决定 value 的转换方式 |
| required | boolean | no | null | 是否必填 |
| remark | string | no | null | 备注说明 |

### VariableType
| Value | 说明 |
| --- | --- |
| String | 直接使用 value |
| Long | Long.valueOf(value) |
| Integer | Integer.valueOf(value) |
| Float | Float.valueOf(value) |
| Double | Double.valueOf(value) |
| Boolean | Boolean.valueOf(value) |
| Array | 暂未实现 |
| Object | 暂未实现 |

## Inputs
无上游输入。前端应禁止向 START 节点连入边。

## Outputs
输出变量名由每个 Param 的 `name` 决定，输出值由 `value` 按 `type` 转换后得到。
一组 params 配置示例：
```json
{"name":"userId","value":"123","type":"Long"}
```
运行时输出为 `{ "userId": 123L }`。

## Runtime
1. 执行 `doExecute`，遍历 `config.params`
2. 对每个 Param，以 `name` 为输出变量名，调用 `createOutputValue` 按 `type` 进行类型转换后写入 `NodeOutput`
3. 标记 `output` 为 COMPLETE 状态

## Verification
配置校验规则（`verifyConfig`）：
1. 参数名称不允许包含下划线 `_`
2. 参数名称不允许重复

## Failures
- `IllegalArgumentException`：遇到不支持的 VariableType（如 Array/Object 尚未实现转换逻辑）
- 运行时 `Exception`：标记节点状态为 FAILED，消息格式为 `{节点名}执行失败: {异常信息}`

## Frontend Notes
- 每个工作流只允许一个 START 节点
- 禁止删除工作流中唯一的 START 节点
