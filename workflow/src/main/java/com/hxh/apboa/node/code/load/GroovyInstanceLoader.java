package com.hxh.apboa.node.code.load;

import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.CodeLanguage;
import com.hxh.apboa.node.code.CodeExecutor;
import groovy.lang.GroovyClassLoader;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 描述：Groovy实例加载器
 *
 * @author huxuehao
 **/
@Component("codeGroovyInstanceLoader")
public class GroovyInstanceLoader implements InstanceLoader {
    private final GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
    private final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    @Override
    public CodeExecutor loadInstance(String codeSource) {
        if (FuncUtils.isNotEmpty(codeSource)) {
            // 基于源码获取Class
            Class<?> clazz = getCodeSourceClass(codeSource);
            if (clazz == null) {
                throw new IllegalArgumentException("loadNewInstance 执行失败, Glue 脚本为空");
            }

            Object instance = getObject(clazz);
            if (!(instance instanceof CodeExecutor)) {
                throw new IllegalArgumentException("loadNewInstance 执行失败, Glue 脚本类需要继承 " + CodeExecutor.class.getName());
            }
            return (CodeExecutor) instance;
        }

        throw new IllegalArgumentException("loadNewInstance 执行失败, Glue 脚本为空");
    }

    /**
     * 获取对象实例
     * @param clazz 类
     * @return 对象实例
     */
    private static Object getObject(Class<?> clazz) {
        Object instance;
        try {
            // 获取无参构造方法
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            // 若构造方法是私有的，需要设置可访问
            constructor.setAccessible(true);
            // 创建实例
            instance =  constructor.newInstance();

        } catch (NoSuchMethodException e) {
            throw new RuntimeException("类 " + clazz.getName() + " 没有无参构造方法", e);
        } catch (InstantiationException e) {
            throw new RuntimeException("类 " + clazz.getName() + " 无法实例化（可能是抽象类或接口）", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("类 " + clazz.getName() + " 的构造方法不可访问", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("类 " + clazz.getName() + " 的构造方法执行失败", e.getTargetException());
        }
        return instance;
    }

    private Class<?> getCodeSourceClass(String codeSource) {
        try {
            // 对codeSource进行md5加密，获取到的字符串作为缓存的key使用
            byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
            String md5Str = new BigInteger(1, md5).toString(16);

            Class<?> clazz = CLASS_CACHE.get(md5Str);
            if (clazz == null) {
                clazz = groovyClassLoader.parseClass(codeSource);
                CLASS_CACHE.putIfAbsent(md5Str, clazz);
            }
            return clazz;
        } catch (Exception e) {
            return groovyClassLoader.parseClass(codeSource);
        }
    }


    @Override
    public CodeLanguage getLanguage() {
        return CodeLanguage.JAVA;
    }
}
