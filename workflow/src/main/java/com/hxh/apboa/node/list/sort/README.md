该节点为列表排序节点。该节点的作用是将输入的列表数据进行排序，并返回排序后的列表数据。

说明：
1. 该节点只支持整数排序，无论是使用 item、item.xxx 还是 GROOVY 表达式，其计算结果必须是整数，否则无法进行排序。
2. config.evaluatorType 目前只支持 GROOVY 类型。
3. config.condition 支持三种形式
   - item 表示当前列表元素
   - item.xxx 表示当前列表元素下的属性
   - GROOVY表达式，只有当 config.condition 是 GROOVY 时 config.evaluatorType才会被用到
4. config.direction 支持两种形式 ASC/DESC
5. config.strictMode 表示是否严格模式，严格模式下，如果 condition 的值不存在，则报错。默认为 false。
6. config.nullFirst 表示是否将 null 值放在最前面。只有当 strictMode 为 false 时，才会生效。默认为 false。

该节点对应的 json 存储示例如下：
```json
{
  "id": "当前节点ID",
  "name": "列表排序",
  "type": "LIST_SORT",
  "config": {
    "evaluatorType": "GROOVY",
    "condition": "item.age",
    "direction": "DESC",
    "strictMode": false,
    "nullFirst": false
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
