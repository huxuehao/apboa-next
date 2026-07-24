package com.hxh.apboa.console.longterm;

import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.entity.LongTermMemoryConfig;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.longterm.service.LongTermMemoryConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：LongTermMemoryConfigController
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/long-term-memory")
@RequiredArgsConstructor
public class LongTermMemoryConfigController {
    private final LongTermMemoryConfigService longTermMemoryConfigService;

    /**
     * 分页查询
     */
    @GetMapping("/list")
    public R<List<LongTermMemoryConfig>> list() {
        return R.data(longTermMemoryConfigService.list());
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<LongTermMemoryConfig> detail(@PathVariable("id") Long id) {
        return R.data(longTermMemoryConfigService.getById(id));
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> save(@RequestBody LongTermMemoryConfig entity) {
        return R.data(longTermMemoryConfigService.save(entity));
    }

    /**
     * 修改
     */
    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody LongTermMemoryConfig entity) {
        return R.data(longTermMemoryConfigService.updateById(entity));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        // deleteByIds 级联清理 agent_long_term_memory 关联,裸 removeByIds 会留悬空引用
        return R.data(longTermMemoryConfigService.deleteByIds(ids));
    }

    /**
     * 被哪些Agent使用
     */
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    @PostMapping("used-with-agent")
    public R<List<Object>> usedWithAgent(@RequestBody List<Long> ids) {
        return R.data(longTermMemoryConfigService.usedWithAgent(ids));
    }
}
