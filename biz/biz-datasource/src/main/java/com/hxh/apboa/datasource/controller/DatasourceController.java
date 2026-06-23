package com.hxh.apboa.datasource.controller;

import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.datasource.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：数据源控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DatasourceController {
    private final DatasourceService datasourceService;

    /**
     * 描述：添加数据源
     *
     * @param datasource 数据源
     * @return 添加结果
     */
    @PostMapping
    public R<?> add(@RequestBody Datasource datasource) {
        return R.data(datasourceService.save(datasource));
    }

    /**
     * 描述：更新数据源
     *
     * @param datasource 数据源
     * @return 更新结果
     */
    @PutMapping
    public R<?> update(@RequestBody Datasource datasource) {
        return R.data(datasourceService.updateById(datasource));
    }

    /**
     * 描述：删除数据源
     *
     * @param datasourceIds 数据源ID
     * @return 删除结果
     */
    @DeleteMapping("{force}")
    public R<?> deleteDatasource(@PathVariable("force") Integer force, @RequestBody List<String> datasourceIds) {
        return R.data(datasourceService.deleteDatasource(force, datasourceIds));
    }

    /**
     * 描述：查询数据源
     *
     * @return 数据源列表
     */
    @GetMapping
    public R<List<Datasource>> list() {
        return R.data(datasourceService.list());
    }

    /**
     * 描述：查询数据源
     *
     * @param datasourceId 数据源ID
     * @return 数据源
     */
    @GetMapping("/{datasourceId}")
    public R<Datasource> get(@PathVariable("datasourceId") String datasourceId) {
        return R.data(datasourceService.getById(datasourceId));
    }

    /**
     * 描述：启用数据源
     *
     * @param datasourceId 数据源ID
     * @param v 启用状态
     * @return 启用结果
     */
    @PutMapping("/{datasourceId}/enable/{v}")
    public R<?> enable(@PathVariable("datasourceId") String datasourceId, @PathVariable("v") Integer v) {
        return R.data(datasourceService.updateEnable(datasourceId,  v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Datasource datasource) {
        return R.fail("暂未实现");
    }
}
