package com.hxh.apboa.node.serialize.serializer;

import com.hxh.apboa.node.serialize.Config;
import com.hxh.apboa.node.serialize.Serializer;

import java.util.Base64;

/**
 * 描述：base64序列化器
 *
 * @author huxuehao
 **/
public class Base64Serializer implements Serializer {
    @Override
    public String serialize(Object data, Config config) throws Exception {
        if (data instanceof String) {
            return Base64.getEncoder().encodeToString(((String) data).getBytes());
        } else if (data instanceof byte[]) {
            return Base64.getEncoder().encodeToString((byte[]) data);
        } else {
            String json = new JsonSerializer().serialize(data, config);
            return Base64.getEncoder().encodeToString(json.getBytes());
        }
    }
}
