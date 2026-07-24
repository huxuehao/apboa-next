package com.hxh.apboa.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hxh.apboa.account.service.TenantInitService;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.HookType;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.enums.SkillType;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.hook.mapper.HookConfigMapper;
import com.hxh.apboa.params.mapper.ParamsMapper;
import com.hxh.apboa.skill.mapper.SkillPackageMapper;
import com.hxh.apboa.tool.mapper.ToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户初始化服务实现 — 从默认租户（id=1）拷贝内置种子数据到新租户
 *
 * @author huxuehao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantInitServiceImpl implements TenantInitService {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final String DEFAULT_TENANT_CODE = "default";

    private final ParamsMapper paramsMapper;
    private final ToolMapper toolMapper;
    private final HookConfigMapper hookConfigMapper;
    private final SkillPackageMapper skillPackageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initTenantData(Long newTenantId) {
        // 显式设置当前租户为默认租户（id=1），确保 TenantLine 拦截器生成正确的 tenant_id = 1 条件，而非 tenant_id = null。
        TenantUtils.setCurrentTenant(DEFAULT_TENANT_ID, DEFAULT_TENANT_CODE);
        try {
            log.info("开始为新租户 {} 初始化种子数据...", newTenantId);

            copyParams(newTenantId);
            copyToolConfigs(newTenantId);
            copyHookConfigs(newTenantId);
            copySkillPackages(newTenantId);

            log.info("租户 {} 种子数据初始化完成", newTenantId);
        } finally {
            TenantUtils.clear();
        }
    }

    // ---- Params: 全局默认 → 租户级 ----

    private void copyParams(Long newTenantId) {
        List<Params> sourceList = paramsMapper.selectList(
                Wrappers.<Params>lambdaQuery().eq(Params::getTenantId, DEFAULT_TENANT_ID));
        if (sourceList.isEmpty()) {
            log.warn("未找到全局默认参数，跳过");
            return;
        }
        for (Params src : sourceList) {
            Params copy = new Params();
            BeanUtils.copyProperties(src, copy);
            copy.setId(IdWorker.getId());
            copy.setTenantId(newTenantId);
            paramsMapper.insert(copy);
        }
        log.info("复制了 {} 条 params", sourceList.size());
    }

    // ---- 内置Tool/Hook（仅复制 GLOBAL 作用域）----
    // 注：启动时 ToolsSyncToDatabase / HooksSyncToDatabase 已按作用域同步到所有租户。
    // 此处为新创建租户补充全局作用域的内置数据：从默认租户(id=1)复制 scope_type='GLOBAL' 的记录。

    private void copyToolConfigs(Long newTenantId) {
        List<ToolConfig> sourceList = toolMapper.selectList(
                Wrappers.<ToolConfig>lambdaQuery()
                        .eq(ToolConfig::getTenantId, DEFAULT_TENANT_ID)
                        .eq(ToolConfig::getToolType, ToolType.BUILTIN)
                        .eq(ToolConfig::getScopeType, ScopeType.GLOBAL));
        if (sourceList.isEmpty()) {
            log.warn("未找到默认租户的全局内置工具配置，跳过");
            return;
        }
        for (ToolConfig src : sourceList) {
            ToolConfig copy = new ToolConfig();
            BeanUtils.copyProperties(src, copy);
            copy.setId(IdWorker.getId());
            copy.setTenantId(newTenantId);
            copy.setCreatedAt(null);
            copy.setUpdatedAt(null);
            copy.setCreatedBy(null);
            copy.setUpdatedBy(null);
            toolMapper.insert(copy);
        }
        log.info("复制了 {} 个 Tool Config", sourceList.size());
    }

    private void copyHookConfigs(Long newTenantId) {
        List<HookConfig> sourceList = hookConfigMapper.selectList(
                Wrappers.<HookConfig>lambdaQuery()
                        .eq(HookConfig::getTenantId, DEFAULT_TENANT_ID)
                        .eq(HookConfig::getHookType, HookType.BUILTIN)
                        .eq(HookConfig::getScopeType, ScopeType.GLOBAL));
        if (sourceList.isEmpty()) {
            log.warn("未找到默认租户的全局内置Hook配置，跳过");
            return;
        }
        for (HookConfig src : sourceList) {
            HookConfig copy = new HookConfig();
            BeanUtils.copyProperties(src, copy);
            copy.setId(IdWorker.getId());
            copy.setTenantId(newTenantId);
            copy.setCreatedAt(null);
            copy.setUpdatedAt(null);
            copy.setCreatedBy(null);
            copy.setUpdatedBy(null);
            hookConfigMapper.insert(copy);
        }
        log.info("复制了 {} 个 Hook Config", sourceList.size());
    }

    private void copySkillPackages(Long newTenantId) {
        List<SkillPackage> sourceList = skillPackageMapper.selectList(
                Wrappers.<SkillPackage>lambdaQuery()
                        .eq(SkillPackage::getTenantId, DEFAULT_TENANT_ID)
                        .eq(SkillPackage::getSkillType, SkillType.BUILTIN)
                        .eq(SkillPackage::getScopeType, ScopeType.GLOBAL));
        if (sourceList.isEmpty()) {
            log.warn("未找到默认租户的全局内置技能包，跳过");
            return;
        }
        for (SkillPackage src : sourceList) {
            SkillPackage copy = new SkillPackage();
            BeanUtils.copyProperties(src, copy);
            copy.setId(IdWorker.getId());
            copy.setTenantId(newTenantId);
            copy.setCreatedAt(null);
            copy.setUpdatedAt(null);
            copy.setCreatedBy(null);
            copy.setUpdatedBy(null);
            skillPackageMapper.insert(copy);
        }
        log.info("复制了 {} 个内置技能包", sourceList.size());
    }
}
