package com.hxh.apboa.node.unserialize.unserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        // 设置为 false 后，Jackson 会忽略 JSON 中的未知属性，只将有对应字段的属性映射到 Java 对象，不抛出异常。
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Object unserialize(String data, Config config) throws Exception {
        return objectMapper.readValue(data, Object.class);
    }
}
