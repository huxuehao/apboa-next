package com.hxh.apboa.node.unserialize.unserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hxh.apboa.node.unserialize.Config;
import com.hxh.apboa.node.unserialize.UnSerializer;

/**
 * 描述：yaml反序列化器
 *
 * @author huxuehao
 **/
public class YamlUnSerializer implements UnSerializer {
    private static final YAMLMapper yamlMapper;
    static {
        yamlMapper = new YAMLMapper();
        // 配置
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 注册Java时间模块
        yamlMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Object unserialize(String data, Config config) throws Exception {
        return yamlMapper.readValue(data, Object.class);
    }
}
