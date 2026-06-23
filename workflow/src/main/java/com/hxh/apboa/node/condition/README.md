该节点为条件判断（if-else）节点。

该节点只支持“一条”分支的条件判断，即该节点的作用等效于 Java 中的如下操作：

```
if (条件判断) {
    下一个节点
} else {
    下一个节点
}
```

该节点对应的 json 存储示例如下：

```
{
  "id": "当前节点ID",
  "name": "条件判断",
  "type": "IF_ELSE",
  "config": {
    "evaluatorType": "GROOVY",
    "conditionExpression": "",
    "allowInputType": [
      "String",
      "Number",
      "Array",
      "Object"
    ],
    "scope": "SELF", // SELF, LENGTH
    "symbol": "GT", // EQ, NE, GT, LT, GE, LE, CONTAINS, NOT_CONTAINS, IS_ALL, STARTS_WITH, ENDS_WITH, EQUALS, NOT_EQUALS, IS_TRUE, IS_FALSE, EXPRESSION
    "compareTo": {
      "type": "CONSTANT", // VARIABLE, CONSTANT
      "value": "常量值或变量名",
      "sourceNodeId": "其他节点ID"
    },
    "inputIsNullUse": false,
    "trueNextNodeId": "下一节点ID",
    "falseNextNodeId": "下一节点ID"
  },
  "inputConfigs": [
    {
      "name": "input",
      "type": "Integer",
      "classify": "NODE_OUTPUT",
      "sourceNodeId": "其他节点ID",
      "sourceOutputName": "其他节点输出名称"
    }
  ],
  "outputConfigs": [
    {
      "fromNodeId": "当前节点ID",
      "name": "output",
      "type": "Boolean"
    }
  ]
}
```

注意：inputConfigs 只支持一个输入，且输入的名称为默认的 "input"；outputConfigs 只支持一个输出，且输入的名称为默认的 "output"。

如果变量的类型为如下的类型，那么其对应的语法格式如下：
1. String
   - 本身（为空时是选择true还是false）
     - 包含（CONTAINS）
     - 不包含（NOT_CONTAINS）
     - 开始是（STARTS_WITH）
     - 结束是（ENDS_WITH）
     - 是（EQUALS）
     - 不是（NOT_EQUALS）
   - 长度（为空时是选择true还是false）
     - 大于（GT）
     - 小于（LT）
     - 大于等于（GE）
     - 小于等于（LE）
     - 等于（EQ）
     - 不等于（NE）
2. Number（为空时是选择true还是false）
   - 大于（GT）
   - 小于（LT）
   - 大于等于（GE）
   - 小于等于（LE）
   - 等于（EQ）
   - 不等于（NE）
3. Array
   - 本身（为空时是选择true还是false）
     - 包含（CONTAINS）
     - 不包含（NOT_CONTAINS）
     - 全部是（IS_ALL）
   - 长度（为空时是选择true还是false）
     - 大于（GT）
     - 小于（LT）
     - 大于等于（GE）
     - 小于等于（LE）
     - 等于（EQ）
     - 不等于（NE）
5. Object：
   - 本身（为空时是选择true还是false）
     - 表达式（EXPRESSION）,表达式中的变量只能使用“item”
6. Boolean:
   - 本身（为空时是选择true还是false）
     -  是true（IS_TRUE）
     -  是false（IS_FALSE）
