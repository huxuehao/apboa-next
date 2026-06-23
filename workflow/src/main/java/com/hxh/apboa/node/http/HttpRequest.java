package com.hxh.apboa.node.http;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：请求参数类
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class HttpRequest {
    private String url;
    private HttpMethod method = HttpMethod.GET;
    private ContentType contentType = ContentType.JSON;
    private List<MapItem> pathParams = new ArrayList<>();
    private List<MapItem> queryParams = new ArrayList<>();
    private List<MapItem> headers = new ArrayList<>();
    private Object body;
}
