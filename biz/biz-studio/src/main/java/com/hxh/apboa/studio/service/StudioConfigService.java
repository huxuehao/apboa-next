package com.hxh.apboa.studio.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.StudioConfig;

import java.util.List;

/**
 * 描述：StudioConfigService
 *
 * @author huxuehao
 **/
public interface StudioConfigService extends IService<StudioConfig> {
    List<Object> usedWithAgent(List<Long> ids);

    /**
     * 删除配置并级联清理 agent_studio 关联(裸 removeByIds 会留悬空引用)
     */
    boolean deleteByIds(List<Long> ids);
}
