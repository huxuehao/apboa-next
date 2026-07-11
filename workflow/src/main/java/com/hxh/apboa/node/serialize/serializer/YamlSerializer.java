package com.hxh.apboa.node.serialize.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hxh.apboa.node.serialize.Config;
import com.hxh.apboa.node.serialize.SerializeMode;
import com.hxh.apboa.node.serialize.Serializer;

/**
 * 描述：yaml序列化器
 *
 * @author huxuehao
 **/
public class YamlSerializer implements Serializer {
    private static final YAMLMapper yamlMapper;
    static {
        yamlMapper = new YAMLMapper();
        // 配置
        yamlMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        yamlMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // 注册Java时间模块
        yamlMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String serialize(Object data, Config config) throws Exception {
        if (data == null) {
            return config.isExcludeNulls() ? "" : "null\n";
        }

        configureSerializationFeatures(config);

        if (config.getMode() == SerializeMode.PRETTY) {
            return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } else {
            return yamlMapper.writeValueAsString(data);
        }
    }

    /**
     * 配置序列化特性
     *
     * @param config 配置
     */
    private void configureSerializationFeatures(Config config) {
        if (config.isExcludeNulls()) {
            yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }

        if (config.isExcludeEmptyStrings()) {
            yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        }
    }
}
