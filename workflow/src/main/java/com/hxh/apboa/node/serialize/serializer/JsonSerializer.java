package com.hxh.apboa.node.serialize.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hxh.apboa.node.serialize.Config;
import com.hxh.apboa.node.serialize.SerializeMode;
import com.hxh.apboa.node.serialize.Serializer;

/**
 * 描述：json序列化器
 *
 * @author huxuehao
 **/
public class JsonSerializer implements Serializer {
    private static final ObjectMapper objectMapper;
    static {
        objectMapper = new ObjectMapper();
        // 注册 Java 8 时间模块，支持 LocalDateTime 等
        objectMapper.registerModule(new JavaTimeModule());
        // 关键：禁用时间戳，使用 ISO-8601 字符串格式
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 设置为 false 后，Jackson 会忽略 “空 Bean” 的限制，直接将其序列化为空 JSON 对象（{}），不抛出异常。
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public String serialize(Object data, Config config) throws Exception {
        if (data == null) {
            return config.isExcludeNulls() ? "" : "{}";
        }

        configureSerializationFeatures(config);

        if (config.getMode() == SerializeMode.PRETTY) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } else {
            return objectMapper.writeValueAsString(data);
        }
    }

    /**
     * 配置序列化特性
     *
     * @param config 配置
     */
    private void configureSerializationFeatures(Config config) {
        if (config.isExcludeNulls()) {
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }

        if (config.isExcludeEmptyStrings()) {
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        }
    }
}
