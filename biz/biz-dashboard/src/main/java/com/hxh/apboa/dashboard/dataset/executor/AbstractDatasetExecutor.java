package com.hxh.apboa.dashboard.dataset.executor;

import com.hxh.apboa.dashboard.dataset.guard.SqlSecurityValidator;
import com.hxh.apboa.dashboard.dataset.guard.TenantPredicateRewriter;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteCommand;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;

import java.util.Map;

/**
 * 描述：数据集执行器模板。统一固化"安全校验 -> 租户谓词注入 -> 组装参数 -> 执行映射"流程，
 * 具体数据源执行细节由子类实现。
 *
 * @author huxuehao
 **/
public abstract class AbstractDatasetExecutor implements DatasetExecutor {
    protected final SqlSecurityValidator validator;
    protected final TenantPredicateRewriter tenantRewriter;

    protected AbstractDatasetExecutor(SqlSecurityValidator validator, TenantPredicateRewriter tenantRewriter) {
        this.validator = validator;
        this.tenantRewriter = tenantRewriter;
    }

    @Override
    public DatasetExecuteResult execute(DatasetExecuteCommand command) {
        String sql = validator.validate(command.getSql());
        // 租户隔离强制：编译期注入 tenant_id 谓词（fail-closed），不依赖 SQL 作者自觉
        String rewritten = tenantRewriter.rewrite(sql);
        Map<String, Object> params = buildParams(command);
        long start = System.currentTimeMillis();
        DatasetExecuteResult result = doExecute(rewritten, params, command.getLimit());
        result.setElapsedMs(System.currentTimeMillis() - start);
        result.setExecutedSql(rewritten);
        return result;
    }

    /**
     * 组装命名参数：用户参数（数据集固定参数 + 面板私有筛选参数） + 自动注入的租户/用户上下文。
     * 系统保留参数不可被外部伪造，由 {@link DatasetParamSupport#mergeParams} 统一保证。
     */
    protected Map<String, Object> buildParams(DatasetExecuteCommand command) {
        return DatasetParamSupport.mergeParams(command.getParams());
    }

    /**
     * 由具体数据源实现真正的执行与结果映射
     */
    protected abstract DatasetExecuteResult doExecute(String sql, Map<String, Object> params, int limit);
}
