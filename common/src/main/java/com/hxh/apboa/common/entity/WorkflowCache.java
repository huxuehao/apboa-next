package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 描述：流程数缓存表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.WORKFLOW_CACHE)
@NoArgsConstructor
public class WorkflowCache extends BaseTenantEntity {
    /**
     * 流程ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String workflowId;
    /**
     * 缓存ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String cacheId;

    public WorkflowCache(String workflowId, String cacheId) {
        this.workflowId = workflowId;
        this.cacheId = cacheId;
    }
}
