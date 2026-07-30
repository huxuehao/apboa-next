package com.hxh.apboa.dashboard.dataset.guard;

import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

/**
 * 描述：仅允许 SELECT 查询语句
 *
 * @author huxuehao
 **/
@Component
public class SelectOnlyGuard implements SqlGuard {
    @Override
    public void check(SqlGuardContext context) {
        if (!(context.getStatement() instanceof Select)) {
            throw new DatasetSecurityException("数据集仅允许 SELECT 查询语句");
        }
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
