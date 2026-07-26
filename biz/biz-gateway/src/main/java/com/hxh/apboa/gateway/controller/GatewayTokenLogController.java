package com.hxh.apboa.gateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.gateway.entity.GatewayTokenLog;
import com.hxh.apboa.gateway.service.GatewayTokenLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：网关Token颁发日志控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/gateway/token-log")
@RequiredArgsConstructor
public class GatewayTokenLogController {
    private final GatewayTokenLogService tokenLogService;

    @GetMapping("/page")
    public R<IPage<GatewayTokenLog>> page(GatewayTokenLog query, PageParams pageParams) {
        return R.data(tokenLogService.pageLogs(query, pageParams));
    }

    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> delete(@RequestBody List<Long> ids) {
        return R.data(tokenLogService.deleteLogs(ids));
    }
}
