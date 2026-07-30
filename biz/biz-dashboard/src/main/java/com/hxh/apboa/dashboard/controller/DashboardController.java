package com.hxh.apboa.dashboard.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.entity.Dashboard;
import com.hxh.apboa.common.entity.DashboardUser;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.dashboard.service.DashboardService;
import com.hxh.apboa.dashboard.vo.PortalDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 描述：Dashboard 模板与门户接口
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/page")
    public R<IPage<Dashboard>> page(PageParams pageParams, Dashboard query) {
        return R.data(dashboardService.page(MP.getPage(pageParams), MP.getQueryWrapper(query)));
    }

    @GetMapping
    public R<List<Dashboard>> list(Dashboard query) {
        return R.data(dashboardService.list(MP.getQueryWrapper(query)));
    }

    @GetMapping("/{id}")
    public R<Dashboard> get(@PathVariable("id") Long id) {
        return R.data(dashboardService.getById(id));
    }

    @PostMapping
    public R<Dashboard> add(@RequestBody Dashboard dashboard) {
        return R.data(dashboardService.saveDashboard(dashboard));
    }

    @PutMapping
    public R<Boolean> update(@RequestBody Dashboard dashboard) {
        return R.data(dashboardService.updateDashboard(dashboard));
    }

    @DeleteMapping("/{force}")
    public R<Boolean> delete(@PathVariable("force") Integer force, @RequestBody List<Long> ids) {
        return R.data(dashboardService.removeDashboards(ids));
    }

    @PutMapping("/{id}/enable/{v}")
    public R<Boolean> enable(@PathVariable("id") Long id, @PathVariable("v") Integer v) {
        return R.data(dashboardService.updateEnable(id, v));
    }

    @PutMapping("/{id}/default")
    public R<Boolean> setDefault(@PathVariable("id") Long id) {
        return R.data(dashboardService.setDefault(id));
    }

    /**
     * 门户解析：当前用户生效的 Dashboard
     */
    @GetMapping("/portal")
    public R<PortalDashboardVO> portal() {
        return R.data(dashboardService.resolvePortal());
    }

    /**
     * 获取当前用户的个人副本
     */
    @GetMapping("/{id}/personal")
    public R<DashboardUser> getPersonal(@PathVariable("id") Long id) {
        return R.data(dashboardService.getPersonal(id));
    }

    /**
     * 保存当前用户的个人副本 DSL
     */
    @PutMapping("/{id}/personal")
    public R<Boolean> savePersonal(@PathVariable("id") Long id, @RequestBody Object config) {
        return R.data(dashboardService.savePersonal(id, config));
    }

    /**
     * 恢复默认（删除个人副本）
     */
    @DeleteMapping("/{id}/personal")
    public R<Boolean> resetPersonal(@PathVariable("id") Long id) {
        return R.data(dashboardService.resetPersonal(id));
    }
}
