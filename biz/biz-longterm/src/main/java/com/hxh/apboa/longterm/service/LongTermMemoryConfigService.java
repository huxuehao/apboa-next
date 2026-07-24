package com.hxh.apboa.longterm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.LongTermMemoryConfig;

import java.util.List;

/**
 * 描述：LongTermMemoryConfigService
 *
 * @author huxuehao
 **/
public interface LongTermMemoryConfigService extends IService<LongTermMemoryConfig> {
    List<Object> usedWithAgent(List<Long> ids);

    /**
     * 删除配置并级联清理 agent_long_term_memory 关联(裸 removeByIds 会留悬空引用)
     */
    boolean deleteByIds(List<Long> ids);
}
