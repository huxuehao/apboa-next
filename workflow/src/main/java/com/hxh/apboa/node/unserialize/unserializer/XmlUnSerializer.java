package com.hxh.apboa.node.unserialize.unserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hxh.apboa.node.unserialize.Config;
import com.hxh.apboa.node.unserialize.UnSerializer;

/**
 * 描述：xml反序列化器
 *
 * @author huxuehao
 **/
public class XmlUnSerializer implements UnSerializer {
    private static final XmlMapper xmlMapper;

    static {
        xmlMapper = new XmlMapper();
        // 配置
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);

        // 注册Java时间模块
        xmlMapper.registerModule(new JavaTimeModule());
    }
    @Override
    public Object unserialize(String data, Config config) throws Exception {
        return xmlMapper.readValue(data, Object.class);
    }
}
