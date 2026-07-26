package com.hxh.apboa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 描述：API网关服务启动类
 * 管理面REST接口由console承载，本服务仅运行Vert.x数据面，因此排除网关管理控制器
 *
 * @author huxuehao
 **/
@SpringBootApplication
@ComponentScan(
        basePackages = "com.hxh.apboa",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.hxh\\.apboa\\.gateway\\.controller\\..*"
        )
)
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
