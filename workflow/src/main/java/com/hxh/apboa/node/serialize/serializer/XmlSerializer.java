package com.hxh.apboa.node.serialize.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hxh.apboa.node.serialize.Config;
import com.hxh.apboa.node.serialize.SerializeMode;
import com.hxh.apboa.node.serialize.Serializer;

import java.io.StringWriter;

/**
 * 描述：xml序列化器
 *
 * @author huxuehao
 **/
public class XmlSerializer implements Serializer {
    private static final XmlMapper xmlMapper;

    static {
        xmlMapper = new XmlMapper();
        // 配置
        xmlMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);

        // 注册Java时间模块
        xmlMapper.registerModule(new JavaTimeModule());
        xmlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String serialize(Object data, Config config) throws Exception {
        if (data == null) {
            return config.isExcludeNulls() ? "" : "<null/>";
        }

        // 配置序列化选项
        configureSerializationFeatures(config);

        StringWriter writer = new StringWriter();
        if (config.getMode() == SerializeMode.PRETTY) {
            xmlMapper.writerWithDefaultPrettyPrinter().writeValue(writer, data);
        } else {
            xmlMapper.writeValue(writer, data);
        }

        return writer.toString();
    }

    /**
     * 配置序列化特性
     *
     * @param config 配置
     */
    private void configureSerializationFeatures(Config config) {
        if (config.isExcludeNulls()) {
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }
        if (config.isExcludeEmptyStrings()) {
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        }
    }
}
