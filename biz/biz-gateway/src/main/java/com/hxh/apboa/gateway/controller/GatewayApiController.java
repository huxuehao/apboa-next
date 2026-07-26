package com.hxh.apboa.gateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.gateway.entity.GatewayApi;
import com.hxh.apboa.gateway.service.GatewayApiService;
import com.hxh.apboa.gateway.vo.GatewayApiVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：网关API管理控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/gateway/api")
@RequiredArgsConstructor
public class GatewayApiController {
    private final GatewayApiService gatewayApiService;

    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> add(@RequestBody GatewayApiVO vo) {
        return R.data(gatewayApiService.saveApi(vo));
    }

    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> update(@RequestBody GatewayApiVO vo) {
        return R.data(gatewayApiService.updateApi(vo));
    }

    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> delete(@RequestBody List<Long> ids) {
        return R.data(gatewayApiService.deleteApis(ids));
    }

    @GetMapping("/page")
    public R<IPage<GatewayApiVO>> page(GatewayApi query, PageParams pageParams) {
        return R.data(gatewayApiService.pageVO(query, pageParams));
    }

    @GetMapping("/{id}")
    public R<GatewayApiVO> get(@PathVariable("id") Long id) {
        return R.data(gatewayApiService.detail(id));
    }

    @PutMapping("/{id}/online/{v}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> online(@PathVariable("id") Long id, @PathVariable("v") Integer v) {
        return R.data(gatewayApiService.updateOnline(id, v));
    }

    @GetMapping("/categories")
    public R<List<String>> categories() {
        return R.data(gatewayApiService.categories());
    }

    @GetMapping("/brief")
    public R<List<GatewayApiVO>> brief() {
        return R.data(gatewayApiService.listBrief());
    }
}
