package com.hxh.apboa.node.http;

/**
 * 描述：回调接口
 *
 * @author huxuehao
 **/
public interface HttpCallback {
    void onSuccess(HttpResponse response);
    void onFailure(HttpResponse response);
}
