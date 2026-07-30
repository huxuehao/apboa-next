package com.hxh.apboa.dashboard.dataset.guard;

import org.springframework.core.Ordered;

/**
 * 描述：SQL 安全校验规则（责任链）。新增安全约束只需新增一个实现并注册为 Bean。
 *
 * @author huxuehao
 **/
public interface SqlGuard extends Ordered {
    /**
     * 校验，不通过时抛出 DatasetSecurityException
     */
    void check(SqlGuardContext context);
}
