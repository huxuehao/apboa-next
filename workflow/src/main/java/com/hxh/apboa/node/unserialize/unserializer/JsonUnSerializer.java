package com.hxh.apboa.node.unserialize.unserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hxh.apboa.node.unserialize.Config;
import com.hxh.apboa.node.unserialize.UnSerializer;

/**
 * 描述：json反序列化器
 *
 * @author huxuehao
 **/
public class JsonUnSerializer implements UnSerializer {
    private static final ObjectMapper objectMapper;
    static {
        objectMapper = new ObjectMapper();
        // 注册 Java 8 时间模块，支持 LocalDateTime 等
        objectMapper.registerModule(new JavaTimeModule());
        // 关键：禁用时间戳，使用 ISO-8601 字符串格式
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 设置为 false 后，Jackson 会忽略 JSON 中的未知属性，只将有对应字段的属性映射到 Java 对象，不抛出异常。
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Object unserialize(String data, Config config) throws Exception {
        return objectMapper.readValue(data, Object.class);
    }
}
