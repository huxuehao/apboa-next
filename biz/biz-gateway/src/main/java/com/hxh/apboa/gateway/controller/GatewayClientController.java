package com.hxh.apboa.gateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.gateway.entity.GatewayClient;
import com.hxh.apboa.gateway.service.GatewayClientService;
import com.hxh.apboa.gateway.vo.GatewayClientVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：网关客户端管理控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/gateway/client")
@RequiredArgsConstructor
public class GatewayClientController {
    private final GatewayClientService gatewayClientService;

    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> add(@RequestBody GatewayClientVO vo) {
        return R.data(gatewayClientService.saveClient(vo));
    }

    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> update(@RequestBody GatewayClientVO vo) {
        return R.data(gatewayClientService.updateClient(vo));
    }

    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> delete(@RequestBody List<Long> ids) {
        return R.data(gatewayClientService.deleteClients(ids));
    }

    @GetMapping("/page")
    public R<IPage<GatewayClientVO>> page(GatewayClient query, PageParams pageParams) {
        return R.data(gatewayClientService.pageVO(query, pageParams));
    }

    @GetMapping("/{id}")
    public R<GatewayClientVO> get(@PathVariable("id") Long id) {
        return R.data(gatewayClientService.detail(id));
    }

    @PutMapping("/{id}/online/{v}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> online(@PathVariable("id") Long id, @PathVariable("v") Integer v) {
        return R.data(gatewayClientService.updateOnline(id, v));
    }

    @PutMapping("/{id}/secret")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<String> regenerateSecret(@PathVariable("id") Long id) {
        return R.data(gatewayClientService.regenerateSecret(id));
    }
}
