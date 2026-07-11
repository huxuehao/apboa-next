package com.hxh.apboa.node.iterate.load;

import com.hxh.apboa.node.base.CodeLanguage;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述：实例加载工厂
 *
 * @author huxuehao
 **/
public class InstanceLoadFactory {
    private static final Map<CodeLanguage, InstanceLoader> loaders = new HashMap<>();

    /**
     * 注册加载器
     */
    public static void registerLoader(InstanceLoader loader) {
        loaders.put(loader.getLanguage(), loader);
    }

    /**
     * 获取加载器
     */
    public static InstanceLoader getInstanceLoader(CodeLanguage language) {
        InstanceLoader instanceLoader = loaders.get(language);
        if (instanceLoader == null) {
            registerLoader(new GroovyInstanceLoader());
            return loaders.get(language);
        }  else {
            return instanceLoader;
        }
    }
}
