package com.hxh.apboa.node.start;

import com.hxh.apboa.node.base.NodeConfig;
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
    /**
     * 协议（HTTP/HTTPS）
     */
    private String protocol;
    /**
     * 方法（GET/POST/PUT/DELETE等）
     */
    private String method;
    /**
     * 路径
     */
    private String path;
    /**
     * 内容类型
     */
    private String contentType;
    /**
     * 请求参数（分为路径参数/请求参数/Header参数）
     */
    private List<Param> params = List.of();
    /**
     * 请求Body
     */
    private byte[] body;
    /**
     * 访问控制
     */
    private AccessLimit accessLimit;
    /**
     * 鉴权
     */
    private Authenticate authenticate;
}
