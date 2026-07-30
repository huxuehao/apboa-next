package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.Dashboard;
import com.hxh.apboa.common.entity.DashboardHistory;
import com.hxh.apboa.common.entity.DashboardUser;
import com.hxh.apboa.common.enums.dashboard.DashboardStatus;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.dashboard.mapper.DashboardHistoryMapper;
import com.hxh.apboa.dashboard.mapper.DashboardMapper;
import com.hxh.apboa.dashboard.mapper.DashboardUserMapper;
import com.hxh.apboa.dashboard.support.DashboardPermission;
import com.hxh.apboa.dashboard.vo.PortalDashboardVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

/**
 * 描述：Dashboard 模板与个人化服务实现
 *
 * @author huxuehao
 **/
@Service
public class DashboardServiceImpl extends ServiceImpl<DashboardMapper, Dashboard> implements DashboardService {
    /** 历史版本保留上限（每用户每看板） */
    private static final int MAX_HISTORY = 30;

    private final DashboardUserMapper dashboardUserMapper;
    private final DashboardHistoryMapper dashboardHistoryMapper;

    public DashboardServiceImpl(DashboardUserMapper dashboardUserMapper,
                                DashboardHistoryMapper dashboardHistoryMapper) {
        this.dashboardUserMapper = dashboardUserMapper;
        this.dashboardHistoryMapper = dashboardHistoryMapper;
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
    @Transactional(rollbackFor = Exception.class)
    public boolean saveVersion(Long dashboardId, Object config, String note) {
        savePersonal(dashboardId, config);
        DashboardHistory history = new DashboardHistory();
        history.setDashboardId(dashboardId);
        history.setConfig(config);
        history.setNote(note);
        dashboardHistoryMapper.insert(history);
        pruneHistory(dashboardId, UserUtils.getId());
        return true;
    }

    @Override
    public List<DashboardHistory> listHistory(Long dashboardId) {
        return dashboardHistoryMapper.selectList(Wrappers.<DashboardHistory>lambdaQuery()
                .eq(DashboardHistory::getDashboardId, dashboardId)
                .eq(DashboardHistory::getCreatedBy, UserUtils.getId())
                .orderByDesc(DashboardHistory::getCreatedAt)
                .orderByDesc(DashboardHistory::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object rollback(Long dashboardId, Long historyId, boolean snapshotCurrent, String note) {
        Long userId = UserUtils.getId();
        DashboardHistory target = dashboardHistoryMapper.selectOne(Wrappers.<DashboardHistory>lambdaQuery()
                .eq(DashboardHistory::getId, historyId)
                .eq(DashboardHistory::getDashboardId, dashboardId)
                .eq(DashboardHistory::getCreatedBy, userId)
                .last("limit 1"));
        if (target == null) {
            return null;
        }
        // 回滚前可选将当前配置存为历史版本，避免误回滚丢失
        if (snapshotCurrent) {
            DashboardUser current = findPersonal(dashboardId, userId);
            if (current != null && current.getConfig() != null) {
                DashboardHistory snapshot = new DashboardHistory();
                snapshot.setDashboardId(dashboardId);
                snapshot.setConfig(current.getConfig());
                snapshot.setNote(note == null || note.isBlank() ? "回滚前自动备份" : note);
                dashboardHistoryMapper.insert(snapshot);
            }
        }
        savePersonal(dashboardId, target.getConfig());
        pruneHistory(dashboardId, userId);
        return target.getConfig();
    }

    @Override
    public boolean deleteHistory(Long dashboardId, Long historyId) {
        return dashboardHistoryMapper.delete(Wrappers.<DashboardHistory>lambdaQuery()
                .eq(DashboardHistory::getId, historyId)
                .eq(DashboardHistory::getDashboardId, dashboardId)
                .eq(DashboardHistory::getCreatedBy, UserUtils.getId())) > 0;
    }

    /**
     * 保留最近 MAX_HISTORY 条，超出按时间升序删除多余旧版本
     */
    private void pruneHistory(Long dashboardId, Long userId) {
        List<DashboardHistory> stale = dashboardHistoryMapper.selectList(Wrappers.<DashboardHistory>lambdaQuery()
                .select(DashboardHistory::getId)
                .eq(DashboardHistory::getDashboardId, dashboardId)
                .eq(DashboardHistory::getCreatedBy, userId)
                .orderByDesc(DashboardHistory::getCreatedAt)
                .orderByDesc(DashboardHistory::getId)
                .last("limit " + MAX_HISTORY + ", 100000"));
        if (!stale.isEmpty()) {
            dashboardHistoryMapper.delete(Wrappers.<DashboardHistory>lambdaQuery()
                    .in(DashboardHistory::getId, stale.stream().map(DashboardHistory::getId).toList()));
        }
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
        // 默认内容置空（无内置默认配置），新用户从空白开始搭建
        dashboard.setConfig(new HashMap<>());
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
