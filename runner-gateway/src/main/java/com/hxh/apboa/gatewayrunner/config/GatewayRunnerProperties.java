package com.hxh.apboa.gatewayrunner.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 描述：网关数据面配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayRunnerProperties {
    private Token token = new Token();

    @Getter
    @Setter
    public static class Token {
        /**
         * Token颁发服务端口
         */
        private int port = 5060;
    }
}
