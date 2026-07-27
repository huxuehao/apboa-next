package com.hxh.apboa.gateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gateway.service.GatewayAccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：网关访问日志控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/gateway/access-log")
@RequiredArgsConstructor
public class GatewayAccessLogController {
    private final GatewayAccessLogService accessLogService;

    @GetMapping("/page")
    public R<IPage<GatewayAccessLog>> page(GatewayAccessLog query, PageParams pageParams) {
        return R.data(accessLogService.pageLogs(query, pageParams));
    }

    @GetMapping("/{id}")
    public R<GatewayAccessLog> get(@PathVariable("id") Long id) {
        return R.data(accessLogService.logDetail(id));
    }

    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> delete(@RequestBody List<Long> ids) {
        return R.data(accessLogService.deleteLogs(ids));
    }
}
