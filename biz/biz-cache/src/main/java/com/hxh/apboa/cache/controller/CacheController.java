package com.hxh.apboa.cache.controller;

import com.hxh.apboa.cache.service.CacheService;
import com.hxh.apboa.common.entity.Cache;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：缓存控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {
    private final CacheService cacheService;

    /**
     * 描述：添加缓存
     *
     * @param cache 缓存
     * @return 添加结果
     */
    @PostMapping
    public R<?> add(@RequestBody Cache cache) {
        return R.data(cacheService.save(cache));
    }

    /**
     * 描述：更新缓存
     *
     * @param cache 缓存
     * @return 更新结果
     */
    @PutMapping
    public R<?> update(@RequestBody Cache cache) {
        return R.data(cacheService.updateById(cache));
    }

    /**
     * 描述：删除缓存
     *
     * @param cacheIds 缓存ID
     * @return 删除结果
     */
    @DeleteMapping("{force}")
    public R<?> deleteCache(@PathVariable("force") Integer force, @RequestBody List<String> cacheIds) {
        return R.data(cacheService.deleteCache(force, cacheIds));
    }

    /**
     * 描述：查询缓存
     *
     * @return 缓存列表
     */
    @GetMapping
    public R<List<Cache>> list() {
        return R.data(cacheService.list());
    }

    /**
     * 描述：查询缓存
     *
     * @param cacheId 缓存ID
     * @return 缓存
     */
    @GetMapping("/{cacheId}")
    public R<Cache> get(@PathVariable("cacheId") String cacheId) {
        return R.data(cacheService.getById(cacheId));
    }

    /**
     * 描述：启用缓存
     *
     * @param cacheId 缓存ID
     * @param v 启用状态
     * @return 启用结果
     */
    @PutMapping("/{cacheId}/enable/{v}")
    public R<?> enable(@PathVariable("cacheId") String cacheId, @PathVariable("v") Integer v) {
        return R.data(cacheService.updateEnable(cacheId,  v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Cache cache) {
        return R.fail("暂未实现");
    }
}
