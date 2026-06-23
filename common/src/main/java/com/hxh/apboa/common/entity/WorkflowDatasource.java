package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 描述：流程数据源表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.WORKFLOW_DATASOURCE)
@NoArgsConstructor
public class WorkflowDatasource extends BaseTenantEntity {
    /**
     * 流程ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String workflowId;
    /**
     * 数据源ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String datasourceId;

    public WorkflowDatasource(String workflowId, String datasourceId) {
        this.workflowId = workflowId;
        this.datasourceId = datasourceId;
    }
}
