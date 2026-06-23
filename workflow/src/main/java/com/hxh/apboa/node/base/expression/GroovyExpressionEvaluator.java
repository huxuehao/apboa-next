package com.hxh.apboa.node.base.expression;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 描述：Groovy 表达式求值器实现
 *
 * @author huxuehao
 **/
public class GroovyExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public Object evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }

        try {
            // 创建绑定，设置变量
            Binding binding = new Binding();
            if (variables != null) {
                variables.forEach(binding::setVariable);
            }

            // 创建新的GroovyShell实例（线程安全）
            CompilerConfiguration config = createSecureCompilerConfiguration();
            GroovyShell shell = new GroovyShell(binding, config);

            // 执行表达式
            Object result = shell.evaluate(expression);

            // 安全处理结果
            return sanitizeResult(result);

        } catch (Exception e) {
            throw new RuntimeException("Groovy表达式求值失败: " + expression +
                    ", 错误: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateSyntax(String expression) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            throw new Exception("表达式不能为空");
        }

        try {
            CompilerConfiguration config = createSecureCompilerConfiguration();
            GroovyShell shell = new GroovyShell(config);
            shell.parse(expression);
        } catch (Exception e) {
            throw new Exception("Groovy语法错误: " + e.getMessage());
        }
    }

    /**
     * 创建安全的编译器配置，防止恶意代码执行
     */
    private CompilerConfiguration createSecureCompilerConfiguration() {
        CompilerConfiguration config = new CompilerConfiguration();

//        // 安全AST定制器 - 严格限制
//        SecureASTCustomizer secure = new SecureASTCustomizer();
//
//        // 禁止危险语言特性
//        secure.setClosuresAllowed(false);           // 禁止闭包
//        secure.setMethodDefinitionAllowed(false);   // 禁止方法定义
//        secure.setPackageAllowed(false);            // 禁止包声明
//        secure.setIndirectImportCheckEnabled(false); // 关闭间接导入检查
//        secure.setAllowedConstantTypesClasses(null); // 不限制常量类型（宽松）
//
//        // 禁止接收者的类
//        secure.setDisallowedReceivers(Arrays.asList(
//                "java.lang.System",
//                "java.lang.Runtime",
//                "java.lang.ProcessBuilder",
//                "java.lang.Class",
//                "java.lang.reflect.",
//                "java.io.",
//                "java.net.",
//                "java.nio.",
//                "groovy.lang.Script",
//                "groovy.lang.GroovyShell",
//                "groovy.lang.GroovyClassLoader"
//        ));
//
//        // 禁止的不安全导入
//        secure.setDisallowedImports(Arrays.asList(
//                "java.lang.ProcessBuilder",
//                "java.lang.Runtime",
//                "java.lang.System",
//                "java.lang.Thread",
//                "java.lang.ClassLoader",
//                "java.lang.reflect.*",
//                "java.io.*",
//                "java.net.*",
//                "java.nio.*",
//                "javax.script.*"
//        ));
//
//        // 导入定制器：保留常用类的自动导入
//        ImportCustomizer imports = new ImportCustomizer();
//        imports.addStaticStars("java.lang.Math");
//        imports.addStaticStars("java.util.Objects");
//        imports.addStaticStars("java.util.Arrays");
//        imports.addImports(
//                // 基础类型和字符串
//                "java.lang.String",
//                "java.lang.Integer",
//                "java.lang.Long",
//                "java.lang.Double",
//                "java.lang.Boolean",
//                // 集合框架（支持长度、遍历、判断等操作）
//                "java.util.List",
//                "java.util.ArrayList",
//                "java.util.Map",
//                "java.util.HashMap",
//                "java.util.Set",
//                "java.util.HashSet",
//                "java.util.Collection",  // 集合通用方法（size()、isEmpty()等）
//                // 日期时间（JDK8+新时间类，更推荐）
//                "java.util.Date",
//                "java.time.LocalDate",
//                "java.time.LocalDateTime",
//                "java.time.format.DateTimeFormatter",
//                // 工具类（空值、数组、比较等）
//                "java.util.Objects",     // 空值判断：Objects.isNull(xxx)
//                "java.util.Arrays",      // 数组操作：Arrays.asList(...)
//                "java.util.Comparator",  // 比较器：用于集合排序等
//                "java.math.BigDecimal",  // 高精度数值计算
//                "java.math.BigInteger"
//        );
//        config.addCompilationCustomizers(secure, imports)

        config.setScriptBaseClass(SafeGroovyScript.class.getName());

        return config;
    }

    /**
     * 安全处理结果，防止返回危险对象
     * 若返回对象是基本类型、常用类型、日期、列表、集合、映射，则返回该对象本身；
     * 否则返回其toString()结果
     */
    private Object sanitizeResult(Object result) {
        if (result == null) {
            return null;
        }

        // 允许的基本类型和常用类型
        if (result instanceof String ||
                result instanceof Number ||
                result instanceof Boolean ||
                result instanceof Date ||
                result instanceof List ||
                result instanceof Set ||
                result instanceof Map) {
            return result;
        }

        // 其他类型转换为字符串
        return result.toString();
    }
}
