package com.hxh.apboa.node.db.delete;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.datasource.mapper.DatasourceMapper;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.db.DBExecutor;
import com.hxh.apboa.node.base.db.DBExecutorFactory;
import com.hxh.apboa.node.base.db.DBNode;
import com.hxh.apboa.node.base.db.DbParam;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 描述：数据库删除节点
 * 根据配置的数据源ID获取数据源连接，执行 SQL 删除操作并返回影响行数。
 *
 * @author huxuehao
 **/
public class DbDeleteNode extends EnhancedNode implements DBNode {

    @Getter
    private final Config config;
    private final TemplateFormatter formatter;
    /** 预计算的类型转换器，与 params 一一对应，运行时零字符串比较 */
    private final List<Function<Object, Object>> typeConverters;

    public DbDeleteNode(String id, String name, Config config) {
        super(id, name, NodeType.DB_DELETE);
        this.config = config;
        this.formatter = TemplateFormatterFactory.createFormatter(config.getFormatterType());
        this.typeConverters = buildConverters(config);
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出
     *
     * @param inputs 框架解析后的输入参数，按 inputConfigs 配置顺序排列
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) {
        DatasourceMapper mapper = SpringContextHolder.getBean(DatasourceMapper.class);
        Datasource datasource = mapper.selectById(config.getDatasourceId());
        if (datasource == null) {
            throw new RuntimeException("数据源不存在，数据源ID: " + config.getDatasourceId());
        }
        if (!Boolean.TRUE.equals(datasource.getEnabled())) {
            throw new RuntimeException("数据源未启用，数据源ID: " + config.getDatasourceId());
        }

        DBExecutor executor = DBExecutorFactory.getExecutor(datasource);

        // 批量解析动态参数
        List<Object> params = resolveParams(inputs);
        int affected = executor.delete(config.getSql(), params);

        // 将执行的 SQL 及参数信息追加到执行上下文中
        output.addExecutionContext("sql", config.getSql());
        output.addExecutionContext("sqlParams", params);

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, affected);
        output.markComplete();
        return output;
    }

    /**
     * 解析 SQL 参数：将 params 序列化为 JSON String，使用模板引擎一次性解析所有动态变量，
     * 再回转为 JSONArray 并按序提取 value 字段。
     */
    private List<Object> resolveParams(Map<String, Object> inputs) {
        if (config.getParams() == null || config.getParams().isEmpty()) {
            return List.of();
        }

        if (inputs == null || inputs.isEmpty()) {
            return config.getParams().stream().map(DbParam::getValue).collect(Collectors.toList());
        }

        String paramsJson = JsonUtils.toJsonStr(config.getParams());
        String resolvedJson = formatter.format(paramsJson, inputs, false).toString();
        List<JsonNode> resolvedArray = JsonUtils.parseArray(resolvedJson, JsonNode.class);
        List<Object> paramValues = new ArrayList<>();
        for (int i = 0; i < resolvedArray.size(); i++) {
            Object rawValue = resolvedArray.get(i).get("value");
            paramValues.add(typeConverters.get(i).apply(rawValue));
        }
        return paramValues;
    }

    /**
     * 预计算类型转换器：根据 params 中各参数的 type 声明，在构造阶段一次性生成转换函数，
     * 运行时直接调用 lambda，避免每次执行时的字符串比较和分支判断。
     */
    private List<Function<Object, Object>> buildConverters(Config config) {
        if (config.getParams() == null || config.getParams().isEmpty()) {
            return List.of();
        }
        List<Function<Object, Object>> converters = new ArrayList<>();
        for (DbParam param : config.getParams()) {
            converters.add(createConverter(param.getType()));
        }
        return List.copyOf(converters);
    }

    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (FuncUtils.isEmpty(config.getDatasourceId())) {
            return VerifyResult.invalid(new VerifyFail("datasourceId", "数据源ID不能为空"));
        }
        if (FuncUtils.isEmpty(config.getSql())) {
            return VerifyResult.invalid(new VerifyFail("sql", "SQL 语句不能为空"));
        }
        return VerifyResult.valid();
    }
}
