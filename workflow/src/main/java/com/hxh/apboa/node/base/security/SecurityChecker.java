package com.hxh.apboa.node.base.security;

/**
 * 描述：代码安全检查接口
 *
 * @author huxuehao
 **/
public interface SecurityChecker {
    SecurityCheckResult checkCodeSecurity(String javaCode);
}
