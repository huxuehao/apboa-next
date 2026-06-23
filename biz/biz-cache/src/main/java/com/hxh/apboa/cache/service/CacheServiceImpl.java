package com.hxh.apboa.cache.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.cache.mapper.CacheMapper;
import com.hxh.apboa.common.entity.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：缓存日志服务实现
 *
 * @author huxuehao
 **/
@Service
public class CacheServiceImpl extends ServiceImpl<CacheMapper, Cache> implements CacheService {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCache(Integer force, List<String> cacheIds) {
        // TODO 需要判断有没有被使用
        if (force == 0) {
            throw new RuntimeException("请选择强制删除");
        }
        return this.removeByIds(cacheIds);
    }

    @Override
    public boolean updateEnable(String cacheId, Integer enable) {
        return lambdaUpdate()
                .set(Cache::getEnabled, enable)
                .eq(Cache::getId, cacheId)
                .update();
    }
}
