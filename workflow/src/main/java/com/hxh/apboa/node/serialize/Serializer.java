package com.hxh.apboa.node.serialize;

/**
 * 描述：序列化接口
 *
 * @author huxuehao
 **/
public interface Serializer {
    String serialize(Object data, Config config) throws Exception;
}
