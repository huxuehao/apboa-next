package com.hxh.apboa.node.base.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Java代码安全检查器
 */
public class SecurityChecker4Java implements SecurityChecker {
    /**
     * 禁止使用的关键字
     */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "Runtime.getRuntime()",
        "ProcessBuilder",
        "System.exit",
        "SecurityManager",
        "setSecurityManager",
        "exec(",
        "fork()",
        "native",
        "sun.misc.Unsafe",
        "jdk.internal",
        "Thread.stop",
        "Thread.destroy",
        "ThreadGroup"
    );

    /**
     * 不允许的包列表
     */
    private static final Set<String> NOT_ALLOWED_PACKAGES = Set.of(
            // 文件系统操作
            "java.io",
            "java.nio.file",
            "java.nio.channels",
            // 网络操作
            "java.net",
            "javax.net",
            "java.rmi",
            "javax.rmi",
            "java.nio.channels.socket",
            // 系统命令与进程
            "java.lang.Process",
            "java.lang.ProcessBuilder",
            "java.lang.Runtime",
            // 安全与权限（修改安全策略，突破限制）
            "java.security",
            "javax.security",
            "java.security.cert",
            "java.security.interfaces",
            // 多线程与并发（可能通过线程耗尽资源）
            "java.util.concurrent",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            // GUI与桌面交互
            "java.awt",
            "javax.swing",
            "java.desktop",
            // 本地方法调用（JNI，直接操作底层系统）
            "java.lang.reflect.Method.invoke", // 间接限制JNI调用
            "sun.jni",
            "com.sun.jna",
            // 其他危险操作（如系统属性修改、日志泄露等）
            "java.util.logging", // 可能通过日志泄露信息
            "javax.management", // JMX可能远程控制
            "java.beans", // 可能通过内省执行危险操作
            "java.time.chrono", // 较少直接风险，但部分场景可能被滥用
            "jdk.nashorn", // 脚本引擎可能执行动态代码
            "javax.script" // 脚本引擎API
    );

    /**
     * 危险操作模式匹配正则表达式
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
        "Runtime\\.getRuntime\\(\\)\\.exec\\s*\\(" +
        "|ProcessBuilder\\s*\\(" +
        "|System\\.exit\\s*\\(" +
        "|new\\s+SecurityManager\\s*\\(" +
        "|System\\.setSecurityManager\\s*\\(" +
        "|Unsafe\\.getUnsafe\\s*\\(" +
        "|@Native" +
        "|sun\\.misc" +
        "|jdk\\.internal"
    );

    /**
     * 检查Java代码的安全性
     */
    public SecurityCheckResult checkCodeSecurity(String javaCode) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1. 基础语法检查
        if (!isValidJavaSyntax(javaCode)) {
            errors.add("代码语法无效");
            return new SecurityCheckResult(false, errors, warnings);
        }

        // 2. 禁止关键字检查
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (javaCode.contains(keyword)) {
                errors.add("检测到禁止使用的关键字: " + keyword);
            }
        }

        // 3. 正则表达式模式匹配
        if (DANGEROUS_PATTERN.matcher(javaCode).find()) {
            errors.add("检测到危险的操作模式");
        }

        // 4. 包导入检查
        if (!checkImports(javaCode, warnings)) {
            errors.add("包含不允许的包导入");
        }

        // 5. 类定义检查
        if (!checkClassDefinition(javaCode)) {
            errors.add("类定义不符合要求");
        }

        // 6. 递归深度检查
        if (hasDeepRecursion(javaCode)) {
            warnings.add("检测到可能的深度递归，可能影响性能");
        }

        // 7. 无限循环检查
        if (hasPotentialInfiniteLoop(javaCode)) {
            warnings.add("检测到可能的无限循环");
        }

        boolean isSafe = errors.isEmpty();
        return new SecurityCheckResult(isSafe, errors, warnings);
    }



    private static boolean isValidJavaSyntax(String code) {
        // 简单的语法检查
        return code.contains("class") &&
               code.contains("{") &&
               code.contains("}") &&
               !code.contains("/*") || code.contains("*/"); // 防止注释嵌套问题
    }

    private static boolean checkImports(String code, List<String> warnings) {
        String[] lines = code.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("import")) {
                String importPackage = line.substring(6).replace(";", "").trim();
                boolean notAllowed = NOT_ALLOWED_PACKAGES.stream()
                    .anyMatch(importPackage::startsWith);

                if (notAllowed) {
                    warnings.add("使用了受限包: " + importPackage);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean checkClassDefinition(String code) {
        // 检查是否只定义了一个public类
        long publicClassCount = Pattern.compile("public\\s+class")
            .matcher(code)
            .results()
            .count();
        return publicClassCount <= 1;
    }

    private static boolean hasDeepRecursion(String code) {
        // 简单的递归模式检测
        return Pattern.compile("\\w+\\s*\\([^)]*\\)\\s*\\{\\s*\\w+\\s*\\(")
            .matcher(code)
            .find();
    }

    private static boolean hasPotentialInfiniteLoop(String code) {
        return Pattern.compile("while\\s*\\(\\s*true\\s*\\)|for\\s*\\([^;]*;[^;]*;\\)")
            .matcher(code)
            .find();
    }
}
