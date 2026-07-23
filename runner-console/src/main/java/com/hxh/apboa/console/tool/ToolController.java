package com.hxh.apboa.console.tool;

import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.dto.ToolDTO;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.vo.ToolVO;
import com.hxh.apboa.tool.service.ToolService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/tool")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public R<IPage<ToolVO>> page(PageParams pageParams, ToolDTO query) {
        IPage<ToolConfig> page = toolService.page(MP.getPage(pageParams), MP.getQueryWrapper(query));
        return R.data(BeanUtils.copyPage(page, ToolVO.class));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<ToolVO> detail(@PathVariable("id") Long id) {
        ToolConfig entity = toolService.getById(id);
        if (entity == null) {
            // 工具不存在（已删除 / 传入陈旧或错误 id / 跨租户不可见）：返回友好错误，
            // 否则 BeanUtils.copy(null) 返回 null，下一行 vo.setUsed 抛 NPE → 500
            return R.fail("工具不存在");
        }

        ToolVO vo = BeanUtils.copy(entity, ToolVO.class);
        vo.setUsed(toolService.usedWithAgent(List.of(id)));

        return R.data(vo);
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> save(@RequestBody ToolConfig entity) {
        entity.setToolType(ToolType.CUSTOM);
        return R.data(toolService.save(entity));
    }

    /**
     * 修改
     */
    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody ToolConfig entity) {
        return R.data(toolService.doUpdate(entity));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.data(toolService.deleteTools(ids));
    }

    /**
     * 被哪些Agent使用
     */
    @PostMapping("used-with-agent")
    public R<List<Object>> usedWithAgent(@RequestBody List<Long> ids) {
        return R.data(toolService.usedWithAgent(ids));
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/get/categories")
    public R<List<String>> listCategories() {
        return R.data(toolService.listCategories());
    }
}
