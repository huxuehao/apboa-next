package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.Dashboard;
import com.hxh.apboa.common.entity.DashboardUser;
import com.hxh.apboa.common.enums.dashboard.DashboardStatus;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.dashboard.mapper.DashboardMapper;
import com.hxh.apboa.dashboard.mapper.DashboardUserMapper;
import com.hxh.apboa.dashboard.support.DashboardPermission;
import com.hxh.apboa.dashboard.support.DashboardSeedLoader;
import com.hxh.apboa.dashboard.vo.PortalDashboardVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：Dashboard 模板与个人化服务实现
 *
 * @author huxuehao
 **/
@Service
public class DashboardServiceImpl extends ServiceImpl<DashboardMapper, Dashboard> implements DashboardService {
    private final DashboardUserMapper dashboardUserMapper;
    private final DashboardSeedLoader seedLoader;

    public DashboardServiceImpl(DashboardUserMapper dashboardUserMapper, DashboardSeedLoader seedLoader) {
        this.dashboardUserMapper = dashboardUserMapper;
        this.seedLoader = seedLoader;
    }

    @Override
    public Dashboard saveDashboard(Dashboard dashboard) {
        DashboardPermission.requireAdmin();
        if (dashboard.getVersion() == null) {
            dashboard.setVersion("1");
        }
        if (dashboard.getStatus() == null) {
            dashboard.setStatus(DashboardStatus.DRAFT);
        }
        if (dashboard.getIsDefault() == null) {
            dashboard.setIsDefault(false);
        }
        save(dashboard);
        return dashboard;
    }

    @Override
    public boolean updateDashboard(Dashboard dashboard) {
        DashboardPermission.requireAdmin();
        // 模板配置变更后递增版本，供个人副本判断是否落后
        dashboard.setVersion(nextVersion(dashboard.getId()));
        return updateById(dashboard);
    }

    @Override
    public boolean removeDashboards(List<Long> ids) {
        DashboardPermission.requireAdmin();
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        return removeByIds(ids);
    }

    @Override
    public boolean updateEnable(Long id, Integer enable) {
        DashboardPermission.requireAdmin();
        return lambdaUpdate()
                .set(Dashboard::getEnabled, enable != null && enable == 1)
                .eq(Dashboard::getId, id)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(Long id) {
        DashboardPermission.requireAdmin();
        // 先清除本租户所有默认标记，再设置目标为默认（租户过滤由拦截器自动追加）
        lambdaUpdate().set(Dashboard::getIsDefault, false).eq(Dashboard::getIsDefault, true).update();
        return lambdaUpdate().set(Dashboard::getIsDefault, true).eq(Dashboard::getId, id).update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalDashboardVO resolvePortal() {
        Dashboard template = findDefaultTemplate();
        if (template == null) {
            template = seedDefaultTemplate();
        }
        Long userId = UserUtils.getId();
        DashboardUser personal = findPersonal(template.getId(), userId);
        if (personal == null) {
            personal = new DashboardUser();
            personal.setDashboardId(template.getId());
            personal.setConfig(template.getConfig());
            personal.setBasedVersion(template.getVersion());
            dashboardUserMapper.insert(personal);
        }
        PortalDashboardVO vo = new PortalDashboardVO();
        vo.setDashboardId(template.getId());
        vo.setSource("PERSONAL");
        vo.setTemplateVersion(template.getVersion());
        vo.setBasedVersion(personal.getBasedVersion());
        vo.setStale(isStale(template.getVersion(), personal.getBasedVersion()));
        vo.setConfig(personal.getConfig());
        return vo;
    }

    @Override
    public DashboardUser getPersonal(Long dashboardId) {
        return findPersonal(dashboardId, UserUtils.getId());
    }

    @Override
    public boolean savePersonal(Long dashboardId, Object config) {
        Long userId = UserUtils.getId();
        DashboardUser existing = findPersonal(dashboardId, userId);
        if (existing == null) {
            Dashboard template = getById(dashboardId);
            DashboardUser personal = new DashboardUser();
            personal.setDashboardId(dashboardId);
            personal.setConfig(config);
            personal.setBasedVersion(template == null ? null : template.getVersion());
            return dashboardUserMapper.insert(personal) > 0;
        }
        existing.setConfig(config);
        return dashboardUserMapper.updateById(existing) > 0;
    }

    @Override
    public boolean resetPersonal(Long dashboardId) {
        Long userId = UserUtils.getId();
        return dashboardUserMapper.delete(Wrappers.<DashboardUser>lambdaQuery()
                .eq(DashboardUser::getDashboardId, dashboardId)
                .eq(DashboardUser::getCreatedBy, userId)) >= 0;
    }

    private Dashboard findDefaultTemplate() {
        return lambdaQuery()
                .eq(Dashboard::getIsDefault, true)
                .eq(Dashboard::getEnabled, true)
                .orderByDesc(Dashboard::getCreatedAt)
                .last("limit 1")
                .one();
    }

    private Dashboard seedDefaultTemplate() {
        Dashboard dashboard = new Dashboard();
        dashboard.setName("默认工作台");
        dashboard.setStatus(DashboardStatus.PUBLISHED);
        dashboard.setIsDefault(true);
        dashboard.setVersion("1");
        dashboard.setConfig(seedLoader.load());
        save(dashboard);
        return dashboard;
    }

    private DashboardUser findPersonal(Long dashboardId, Long userId) {
        return dashboardUserMapper.selectOne(Wrappers.<DashboardUser>lambdaQuery()
                .eq(DashboardUser::getDashboardId, dashboardId)
                .eq(DashboardUser::getCreatedBy, userId)
                .last("limit 1"));
    }

    private boolean isStale(String templateVersion, String basedVersion) {
        return templateVersion != null && !templateVersion.equals(basedVersion);
    }

    private String nextVersion(Long dashboardId) {
        Dashboard current = getById(dashboardId);
        String version = current == null ? null : current.getVersion();
        if (version == null) {
            return "1";
        }
        try {
            return String.valueOf(Integer.parseInt(version.trim()) + 1);
        } catch (NumberFormatException e) {
            return version + ".1";
        }
    }
}
