package com.hxh.apboa.cache.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.Cache;

import java.util.List;

/**
 * 描述：缓存日志服务
 *
 * @author huxuehao
 **/
public interface CacheService extends IService<Cache> {
    /**
     * 删除缓存
     *
     * @param cacheIds 缓存ID
     * @return 是否添加成功
     */
    boolean deleteCache(Integer force, List<String> cacheIds);

    /**
     * 更新缓存是否可以
     *
     * @param cacheId 缓存ID
     * @param enable       状态值
     * @return 是否删除成功
     */
    boolean updateEnable(String cacheId, Integer enable);
}
