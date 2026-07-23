package com.hxh.apboa.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApboaSpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static String getProperty(String key) {
        return context.getEnvironment().getProperty(key);
    }

    public static ObjectMapper getObjectMapper() {
         return context.getBean(ObjectMapper.class);
    }

    /** 通用取 bean（供非 Spring 管理对象如 SubAgentTool、静态解析器访问容器组件） */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
