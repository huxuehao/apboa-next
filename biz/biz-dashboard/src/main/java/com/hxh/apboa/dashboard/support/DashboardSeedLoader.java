package com.hxh.apboa.dashboard.support;

import com.hxh.apboa.common.util.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 描述：内置默认 Dashboard DSL 种子加载器。租户无模板时据此生成默认模板。
 *
 * @author huxuehao
 **/
@Component
public class DashboardSeedLoader {
    private static final String SEED_PATH = "dashboard/default-template.json";

    private volatile Object seedConfig;

    /**
     * 加载内置默认 DSL（懒加载并缓存）
     */
    public Object load() {
        if (seedConfig == null) {
            synchronized (this) {
                if (seedConfig == null) {
                    seedConfig = readSeed();
                }
            }
        }
        return seedConfig;
    }

    private Object readSeed() {
        try (InputStream is = new ClassPathResource(SEED_PATH).getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return JsonUtils.parse(json);
        } catch (Exception e) {
            throw new RuntimeException("加载默认 Dashboard 模板失败", e);
        }
    }
}
