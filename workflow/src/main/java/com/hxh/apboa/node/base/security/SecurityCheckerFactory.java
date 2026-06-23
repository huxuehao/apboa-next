package com.hxh.apboa.node.base.security;

import com.hxh.apboa.node.base.CodeLanguage;

import java.util.Objects;

/**
 * 描述： 代码安全检查工厂
 *
 * @author huxuehao
 **/
public class SecurityCheckerFactory {
    private static final SecurityChecker4Java securityChecker4Java = new SecurityChecker4Java();

    public static SecurityChecker getChecker(CodeLanguage language) {
        if (Objects.requireNonNull(language) == CodeLanguage.JAVA) {
            return securityChecker4Java;
        }
        throw new IllegalArgumentException("不支持的语言");
    }
}
