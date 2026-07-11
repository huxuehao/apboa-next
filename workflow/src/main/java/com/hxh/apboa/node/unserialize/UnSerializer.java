package com.hxh.apboa.node.unserialize;

/**
 * 描述：序列化接口
 *
 * @author huxuehao
 **/
public interface UnSerializer {
    Object unserialize(String data, Config config) throws Exception;
}
