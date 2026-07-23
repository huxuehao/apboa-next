package com.hxh.apboa.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.CodeExecutionConfig;

import java.util.List;

/**
 * 描述：CodeExecutionConfigService
 *
 * @author huxuehao
 **/
public interface CodeExecutionConfigService extends IService<CodeExecutionConfig> {
    /**
     * 查询被哪些Agent使用
     *
     * @param ids 配置ID列表
     * @return Agent名称列表
     */
    List<Object> usedWithAgent(List<Long> ids);

    /**
     * 删除配置并级联清理 agent_code_execution 关联(裸 removeByIds 会留悬空引用)
     */
    boolean deleteByIds(List<Long> ids);
}
