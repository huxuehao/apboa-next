package com.hxh.apboa.dashboard.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.entity.DashboardDataset;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import com.hxh.apboa.dashboard.dataset.model.DatasetPreviewRequest;
import com.hxh.apboa.dashboard.dataset.model.DatasetQueryRequest;
import com.hxh.apboa.dashboard.service.DashboardDatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 描述：Dashboard 数据集接口
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/dashboard/dataset")
@RequiredArgsConstructor
public class DashboardDatasetController {
    private final DashboardDatasetService datasetService;

    @GetMapping("/page")
    public R<IPage<DashboardDataset>> page(PageParams pageParams, DashboardDataset query) {
        return R.data(datasetService.page(MP.getPage(pageParams), MP.getQueryWrapper(query)));
    }

    @GetMapping
    public R<List<DashboardDataset>> list(DashboardDataset query) {
        return R.data(datasetService.list(MP.getQueryWrapper(query)));
    }

    @GetMapping("/{id}")
    public R<DashboardDataset> get(@PathVariable("id") Long id) {
        return R.data(datasetService.getById(id));
    }

    @PostMapping
    public R<DashboardDataset> add(@RequestBody DashboardDataset dataset) {
        return R.data(datasetService.saveDataset(dataset));
    }

    @PutMapping
    public R<Boolean> update(@RequestBody DashboardDataset dataset) {
        return R.data(datasetService.updateDataset(dataset));
    }

    @DeleteMapping
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.data(datasetService.removeDatasets(ids));
    }

    @PutMapping("/{id}/enable/{v}")
    public R<Boolean> enable(@PathVariable("id") Long id, @PathVariable("v") Integer v) {
        return R.data(datasetService.updateEnable(id, v));
    }

    /**
     * 即席预览执行
     */
    @PostMapping("/execute")
    public R<DatasetExecuteResult> execute(@RequestBody DatasetPreviewRequest request,
                                           @RequestHeader(value = "Origin", required = false) String origin,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        request.setCallerOrigin(origin);
        request.setCallerToken(authorization);
        return R.data(datasetService.preview(request));
    }

    /**
     * 按已保存数据集取数（面板运行态）
     */
    @PostMapping("/{id}/query")
    public R<DatasetExecuteResult> query(@PathVariable("id") Long id,
                                         @RequestBody(required = false) DatasetQueryRequest request,
                                         @RequestHeader(value = "Origin", required = false) String origin,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        DatasetQueryRequest req = request == null ? new DatasetQueryRequest() : request;
        req.setCallerOrigin(origin);
        req.setCallerToken(authorization);
        return R.data(datasetService.queryById(id, req));
    }
}
