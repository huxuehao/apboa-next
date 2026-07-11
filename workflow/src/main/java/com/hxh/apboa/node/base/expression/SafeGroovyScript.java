package com.hxh.apboa.node.base.expression;

import groovy.lang.Script;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 描述：安全的Groovy脚本基类，限制危险操作
 *
 * @author huxuehao
 **/
public abstract class SafeGroovyScript extends Script {
    // 禁止的危险方法
    private static final Set<String> FORBIDDEN_METHODS = new HashSet<>(Arrays.asList(
            // Object 类危险方法
            "getClass", "wait", "notify", "notifyAll", "clone", "finalize",
            // Groovy 元编程方法
            "getMetaClass", "setMetaClass", "invokeMethod", "getProperty", "setProperty",
            "getAttributes", "setAttributes",
            // 反射相关方法
            "getDeclaredFields", "getDeclaredMethods", "getDeclaredConstructors",
            "getField", "getMethod", "newInstance",
            // 系统操作方法
            "exit", "exec", "gc", "load", "loadLibrary", "runFinalization",
            "start", "stop", "destroy", "fork", "spawn"
    ));

    // 禁止的类（使用完整类名）
    private static final Set<String> FORBIDDEN_CLASSES = Set.of(
            "java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder",
            "java.lang.ClassLoader", "java.lang.Thread", "java.lang.Process",
            "java.lang.reflect.", "java.io.", "java.net.", "java.nio.",
            "groovy.lang.GroovyShell", "groovy.lang.GroovyClassLoader",
            "groovy.util.Eval", "javax.script."
    );

    private final ThreadLocal<Boolean> inMethodCall = ThreadLocal.withInitial(() -> false);

    @Override
    public Object invokeMethod(String name, Object args) {
        // 如果已经在处理一个方法调用，直接跳过安全检查
        if (inMethodCall.get()) {
            return doUnsafeMethodCall(name, args);
        }

        try {
            // 设置标志：开始安全检查
            inMethodCall.set(true);
            return doSafeMethodCall(name, args);
        } finally {
            // 清除标志：安全检查结束
            inMethodCall.set(false);
        }
    }

    /**
     * 安全地调用方法
     */
    private Object doSafeMethodCall(String name, Object args) {
        // 执行安全检查
        if (FORBIDDEN_METHODS.contains(name)) {
            throw new SecurityException("禁止调用方法: " + name);
        }

        checkForbiddenClassesInStack();

        return doUnsafeMethodCall(name, args);
    }

    /**
     * 不安全的调用方法
     */
    private Object doUnsafeMethodCall(String name, Object args) {
        // 直接调用父类方法，不进行安全检查
        return super.invokeMethod(name, args);
    }

    /**
     * 检查调用栈中是否包含危险类
     */
    private void checkForbiddenClassesInStack() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (int i = 3; i < Math.min(stackTrace.length, 15); i++) { // 跳过内部调用
            String className = stackTrace[i].getClassName();

            // 精确匹配禁止的类
            for (String forbiddenClass : FORBIDDEN_CLASSES) {
                if (forbiddenClass.endsWith(".")) {
                    // 包名匹配
                    if (className.startsWith(forbiddenClass)) {
                        throw new SecurityException("禁止访问类: " + className);
                    }
                } else {
                    // 完整类名匹配
                    if (className.equals(forbiddenClass)) {
                        throw new SecurityException("禁止访问类: " + className);
                    }
                }
            }
        }
    }

    @Override
    public Object getProperty(String property) {
        // 限制访问危险属性
        if (property.startsWith("$") || property.contains("class")) {
            throw new SecurityException("禁止访问属性: " + property);
        }
        return super.getProperty(property);
    }
}
