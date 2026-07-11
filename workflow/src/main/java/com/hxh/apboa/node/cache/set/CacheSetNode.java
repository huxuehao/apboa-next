package com.hxh.apboa.node.cache.set;

import com.hxh.apboa.cache.mapper.CacheMapper;
import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.entity.Cache;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.cache.RedisOperator;
import com.hxh.apboa.node.base.cache.RedisOperatorFactory;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 描述：缓存设置节点
 * 根据配置的缓存ID获取 Redis 连接，向 Redis 中设置键值对，支持过期时间配置。
 *
 * @author huxuehao
 **/
public class CacheSetNode extends EnhancedNode {

    @Getter
    private final Config config;
    private final TemplateFormatter formatter;

    public CacheSetNode(String id, String name, Config config) {
        super(id, name, NodeType.CACHE_SET);
        this.config = config;
        this.formatter = TemplateFormatterFactory.createFormatter(config.getFormatterType());
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
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) {
        CacheMapper mapper = SpringContextHolder.getBean(CacheMapper.class);
        Cache cache = mapper.selectById(config.getCacheId());
        if (cache == null) {
            throw new RuntimeException("缓存配置不存在，缓存ID: " + config.getCacheId());
        }
        if (!Boolean.TRUE.equals(cache.getEnabled())) {
            throw new RuntimeException("缓存未启用，缓存ID: " + config.getCacheId());
        }

        RedisOperator operator = RedisOperatorFactory.getOperator(cache);
        String resolvedKey = resolveTemplate(config.getKey(), inputs);
        Object resolvedValue = resolveValue(config.getValue(), inputs);

        if (config.getExpire() != null && config.getExpire() > 0) {
            operator.setEx(resolvedKey, resolvedValue, config.getExpire(), TimeUnit.SECONDS);
        } else {
            operator.set(resolvedKey, resolvedValue);
        }

        // 将缓存设置信息追加到执行上下文中
        output.addExecutionContext("cacheKey", resolvedKey);
        output.addExecutionContext("cacheValue", resolvedValue);
        output.addExecutionContext("expireSeconds", config.getExpire());

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, true);
        output.markComplete();
        return output;
    }

    /**
     * 解析模板字符串中的动态变量
     */
    private String resolveTemplate(String template, Map<String, Object> inputs) {
        if (template == null || inputs == null || inputs.isEmpty()) {
            return template;
        }
        Object resolved = formatter.format(template, inputs, false);
        return resolved != null ? resolved.toString() : template;
    }

    /**
     * 解析 value 中的动态变量
     * 如果 value 是字符串，直接进行模板解析；
     * 如果是复杂对象，先序列化为 JSON 再进行模板解析，然后反序列化回来。
     */
    private Object resolveValue(Object value, Map<String, Object> inputs) {
        if (value == null || inputs == null || inputs.isEmpty()) {
            return value;
        }
        if (value instanceof String str) {
            return resolveTemplate(str, inputs);
        }
        String json = JsonUtils.toJsonStr(value);
        String resolvedJson = resolveTemplate(json, inputs);
        return JsonUtils.parse(resolvedJson);
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
        if (FuncUtils.isEmpty(config.getCacheId())) {
            return VerifyResult.invalid(new VerifyFail("cacheId", "缓存ID不能为空"));
        }
        if (FuncUtils.isEmpty(config.getKey())) {
            return VerifyResult.invalid(new VerifyFail("key", "缓存键不能为空"));
        }
        return VerifyResult.valid();
    }
}
