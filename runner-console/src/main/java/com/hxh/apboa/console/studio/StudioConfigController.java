package com.hxh.apboa.console.studio;

import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.entity.StudioConfig;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.studio.service.StudioConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：StudioConfigController
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/studio")
@RequiredArgsConstructor
public class StudioConfigController {
    private final StudioConfigService studioConfigService;

    /**
     * 分页查询
     */
    @GetMapping("/list")
    public R<List<StudioConfig>> page() {
        return R.data(studioConfigService.list());
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<StudioConfig> detail(@PathVariable("id") Long id) {
        return R.data(studioConfigService.getById(id));
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> save(@RequestBody StudioConfig entity) {
        return R.data(studioConfigService.save(entity));
    }

    /**
     * 修改
     */
    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody StudioConfig entity) {
        return R.data(studioConfigService.updateById(entity));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        // deleteByIds 级联清理 agent_studio 关联,裸 removeByIds 会留悬空引用
        return R.data(studioConfigService.deleteByIds(ids));
    }

    /**
     * 被哪些Agent使用
     */
    @PostMapping("used-with-agent")
    public R<List<Object>> usedWithAgent(@RequestBody List<Long> ids) {
        return R.data(studioConfigService.usedWithAgent(ids));
    }
}
