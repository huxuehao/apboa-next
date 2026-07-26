package com.hxh.apboa.gateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.gateway.entity.GatewayApp;
import com.hxh.apboa.gateway.service.GatewayAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：网关应用管理控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/gateway/app")
@RequiredArgsConstructor
public class GatewayAppController {
    private final GatewayAppService gatewayAppService;

    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> add(@RequestBody GatewayApp app) {
        return R.data(gatewayAppService.saveApp(app));
    }

    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> update(@RequestBody GatewayApp app) {
        return R.data(gatewayAppService.updateApp(app));
    }

    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> delete(@RequestBody List<Long> ids) {
        return R.data(gatewayAppService.deleteApps(ids));
    }

    @GetMapping("/page")
    public R<IPage<GatewayApp>> page(GatewayApp query, PageParams pageParams) {
        return R.data(gatewayAppService.page(MP.getPage(pageParams), MP.getQueryWrapper(query)));
    }

    @GetMapping
    public R<List<GatewayApp>> list() {
        return R.data(gatewayAppService.lambdaQuery().orderByDesc(GatewayApp::getCreatedAt).list());
    }

    @GetMapping("/{id}")
    public R<GatewayApp> get(@PathVariable("id") Long id) {
        return R.data(gatewayAppService.getById(id));
    }

    @PutMapping("/{id}/online/{v}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> online(@PathVariable("id") Long id, @PathVariable("v") Integer v) {
        return R.data(gatewayAppService.updateOnline(id, v));
    }
}
