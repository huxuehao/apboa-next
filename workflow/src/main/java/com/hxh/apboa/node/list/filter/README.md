该节点为列表过滤节点。该节点的作用是将输入的列表数据进行过滤，并返回过滤后的列表数据。
config.mode
 - SIMPLE: 简单过滤，condition只支持一下两种情况。
    - item：列表中元素本身
    - item.xxx：列表中元素的属性
 - EXPRESSION: 评估过滤，通过评估输入列表数据中的元素值和条件进行过滤。
config.supportType
 - STRING: 字符串类型，condition的结果为字符串
 - NUMBER: 数值类型，condition的结果为数值
 - BOOLEAN: 布尔类型，condition的结果为布尔
config.itemIsNullUse
 - true: 当列表元素为 null 时，返回 true
 - false: 当列表元素为 null 时，返回 false
config.simpleSymbol：当mode为SIMPLE时的比较符号
config.evaluatorType: 当mode为EXPRESSION时，evaluatorType为评估器类型，目前支持：GROOVY
config.condition
 - 当mode为SIMPLE时，condition只支持下两种情况：item、item.xxx
 - 当mode为EXPRESSION时，condition为表达式，condition的结果为布尔
config.compareTo
 - 当mode为SIMPLE时，compareTo为比较符元素，支持常量和变量（其他节点的输出）
 - 当mode为EXPRESSION时，compareTo不可用
该节点对应的 json 存储示例如下：

```json
{
  "id": "当前节点ID",
  "name": "列表过滤",
  "type": "LIST_FILTER",
  "config": {
    "mode": "SIMPLE",
    "supportType": "STRING",
    "itemIsNullUse": false,
    "simpleSymbol": "GT",
    "evaluatorType": "",
    "condition": "item.age",
    "compareTo": {
      "type": "VARIABLE",
      "sourceNodeId": "其他节点ID",
      "value": "其他节点输出名称"
    }
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "Array",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点输出名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Array"
    }
  ]
}
```
