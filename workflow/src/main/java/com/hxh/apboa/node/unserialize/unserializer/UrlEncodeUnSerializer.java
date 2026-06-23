package com.hxh.apboa.node.unserialize.unserializer;

import com.hxh.apboa.node.unserialize.Config;
import com.hxh.apboa.node.unserialize.UnSerializer;

import java.net.URLDecoder;

/**
 * 描述：url_encode反序列化器
 *
 * @author huxuehao
 **/
public class UrlEncodeUnSerializer implements UnSerializer {
    @Override
    public Object unserialize(String data, Config config) throws Exception {
        if (data == null) {
            return null;
        }
        return URLDecoder.decode(data, config.getEncoding());
    }
}
