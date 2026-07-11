package com.hxh.apboa.datasource.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.datasource.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DatasourceController {
    private final DatasourceService datasourceService;

    @PostMapping
    public R<?> add(@RequestBody Datasource datasource) {
        return R.data(datasourceService.save(datasource));
    }

    @PutMapping
    public R<?> update(@RequestBody Datasource datasource) {
        return R.data(datasourceService.updateDatasource(datasource));
    }

    @DeleteMapping("{force}")
    public R<?> deleteDatasource(@PathVariable("force") Integer force, @RequestBody List<String> datasourceIds) {
        return R.data(datasourceService.deleteDatasource(force, datasourceIds));
    }

    @GetMapping("/page")
    public R<IPage<Datasource>> page(PageParams pageParams, Datasource query) {
        return R.data(datasourceService.page(MP.getPage(pageParams), MP.getQueryWrapper(query)));
    }

    @GetMapping
    public R<List<Datasource>> list(@RequestParam(value = "enabled", required = false) Integer enabled) {
        return R.data(datasourceService.listByEnabled(enabled));
    }

    @GetMapping("/{datasourceId}")
    public R<Datasource> get(@PathVariable("datasourceId") String datasourceId) {
        return R.data(datasourceService.getById(datasourceId));
    }

    @PutMapping("/{datasourceId}/enable/{v}")
    public R<?> enable(@PathVariable("datasourceId") String datasourceId, @PathVariable("v") Integer v) {
        return R.data(datasourceService.updateEnable(datasourceId, v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Datasource datasource) {
        return R.data(datasourceService.checkConnect(datasource));
    }

    @PostMapping("/{datasourceId}/check/connect")
    public R<?> checkSavedConnect(@PathVariable("datasourceId") String datasourceId) {
        return R.data(datasourceService.checkSavedConnect(datasourceId));
    }
}
