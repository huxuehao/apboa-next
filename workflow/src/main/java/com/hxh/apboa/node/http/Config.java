package com.hxh.apboa.node.http;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：配置类
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    private FormatterType formatterType = FormatterType.STRING;
    private int connectTimeout = 10; // 单位：秒
    private int readTimeout = 30; // 单位：秒
    private int writeTimeout = 30; // 单位：秒
    private int maxRetries = 3;
    private List<Integer> retryStatusCodes; // 重试的响应码
    private boolean followRedirects = true; // 是否跟随重定向
    private boolean syncExecute = true; // 是否同步执行
    private boolean bodyToObject = true; // 是否将响应体转为JSON
    private HttpRequest request;
}
