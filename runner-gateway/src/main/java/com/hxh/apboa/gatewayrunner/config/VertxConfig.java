package com.hxh.apboa.gatewayrunner.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 描述：Vertx实例配置
 * 数据面所有HTTP服务共享同一个Vertx实例
 *
 * @author huxuehao
 **/
@Configuration
public class VertxConfig {

    @Bean(destroyMethod = "close")
    public Vertx vertx() {
        VertxOptions options = new VertxOptions()
                .setPreferNativeTransport(true);
        return Vertx.vertx(options);
    }
}
