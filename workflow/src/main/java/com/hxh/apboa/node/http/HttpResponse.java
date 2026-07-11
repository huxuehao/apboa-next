package com.hxh.apboa.node.http;

import lombok.Getter;
import okhttp3.Headers;
import okhttp3.Response;

/**
 * 描述：响应类
 *
 * @author huxuehao
 **/
@Getter
public class HttpResponse {
    private boolean success;
    private int code;
    private String message;
    private String body;
    private Headers headers;
    private long elapsedTime;
    private Exception error;

    public static HttpResponse success(Response response, String body, long elapsedTime) {
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.success = true;
        httpResponse.code = response.code();
        httpResponse.message = response.message();
        httpResponse.body = body;
        httpResponse.headers = response.headers();
        httpResponse.elapsedTime = elapsedTime;
        return httpResponse;
    }

    public static HttpResponse failure(Exception error, long elapsedTime) {
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.success = false;
        httpResponse.error = error;
        httpResponse.elapsedTime = elapsedTime;
        return httpResponse;
    }

    public static HttpResponse failure(int code, String message, String body, long elapsedTime) {
        HttpResponse httpResponse = new HttpResponse();
        httpResponse.success = false;
        httpResponse.code = code;
        httpResponse.message = message;
        httpResponse.body = body;
        httpResponse.elapsedTime = elapsedTime;
        return httpResponse;
    }
}
