package com.hxh.apboa.cache.controller;

import com.hxh.apboa.cache.service.CacheService;
import com.hxh.apboa.common.entity.Cache;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {
    private final CacheService cacheService;

    @PostMapping
    public R<?> add(@RequestBody Cache cache) {
        return R.data(cacheService.save(cache));
    }

    @PutMapping
    public R<?> update(@RequestBody Cache cache) {
        return R.data(cacheService.updateById(cache));
    }

    @DeleteMapping("{force}")
    public R<?> deleteCache(@PathVariable("force") Integer force, @RequestBody List<String> cacheIds) {
        return R.data(cacheService.deleteCache(force, cacheIds));
    }

    @GetMapping
    public R<List<Cache>> list(@RequestParam(value = "enabled", required = false) Integer enabled) {
        return R.data(cacheService.listByEnabled(enabled));
    }

    @GetMapping("/{cacheId}")
    public R<Cache> get(@PathVariable("cacheId") String cacheId) {
        return R.data(cacheService.getById(cacheId));
    }

    @PutMapping("/{cacheId}/enable/{v}")
    public R<?> enable(@PathVariable("cacheId") String cacheId, @PathVariable("v") Integer v) {
        return R.data(cacheService.updateEnable(cacheId, v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Cache cache) {
        return R.data(cacheService.checkConnect(cache));
    }
}
