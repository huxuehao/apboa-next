package com.hxh.apboa.node.unserialize.unserializer;

import com.hxh.apboa.node.unserialize.Config;
import com.hxh.apboa.node.unserialize.UnSerializer;

import java.util.Base64;

/**
 * 描述：base64反序列化器
 *
 * @author huxuehao
 **/
public class Base64UnSerializer implements UnSerializer {
    @Override
    public Object unserialize(String data, Config config) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(data);
        return new String(decoded, config.getEncoding());
    }
}
