package com.hxh.apboa.dashboard.dataset.executor;

import com.hxh.apboa.dashboard.dataset.guard.SqlSecurityValidator;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteCommand;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;

import java.util.Map;

/**
 * 描述：数据集执行器模板。统一固化"安全校验 -> 组装参数 -> 执行映射"流程，
 * 具体数据源执行细节由子类实现。
 *
 * @author huxuehao
 **/
public abstract class AbstractDatasetExecutor implements DatasetExecutor {
    protected final SqlSecurityValidator validator;

    protected AbstractDatasetExecutor(SqlSecurityValidator validator) {
        this.validator = validator;
    }

    @Override
    public DatasetExecuteResult execute(DatasetExecuteCommand command) {
        String sql = validator.validate(command.getSql());
        Map<String, Object> params = buildParams(command);
        long start = System.currentTimeMillis();
        DatasetExecuteResult result = doExecute(sql, params, command.getLimit());
        result.setElapsedMs(System.currentTimeMillis() - start);
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
