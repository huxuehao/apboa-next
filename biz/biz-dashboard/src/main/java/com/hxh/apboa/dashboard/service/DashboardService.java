package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.Dashboard;
import com.hxh.apboa.common.entity.DashboardHistory;
import com.hxh.apboa.common.entity.DashboardUser;
import com.hxh.apboa.dashboard.vo.PortalDashboardVO;

import java.util.List;

/**
 * 描述：Dashboard 模板与个人化服务
 *
 * @author huxuehao
 **/
public interface DashboardService extends IService<Dashboard> {
    /**
     * 新增模板（需管理员）
     */
    Dashboard saveDashboard(Dashboard dashboard);

    /**
     * 更新模板并自动递增版本（需管理员）
     */
    boolean updateDashboard(Dashboard dashboard);

    /**
     * 删除模板（需管理员）
     */
    boolean removeDashboards(List<Long> ids);

    /**
     * 启停模板（需管理员）
     */
    boolean updateEnable(Long id, Integer enable);

    /**
     * 设为租户默认模板（需管理员）
     */
    boolean setDefault(Long id);

    /**
     * 解析当前用户生效的门户 Dashboard（个人副本优先，必要时种子生成/克隆）
     */
    PortalDashboardVO resolvePortal();

    /**
     * 获取当前用户在指定模板下的个人副本，可能为空
     */
    DashboardUser getPersonal(Long dashboardId);

    /**
     * 保存当前用户的个人副本 DSL
     */
    boolean savePersonal(Long dashboardId, Object config);

    /**
     * 保存为历史版本：更新当前副本并新增一条快照（保留最近若干条）
     */
    boolean saveVersion(Long dashboardId, Object config, String note);

    /**
     * 列出当前用户在指定看板下的历史版本（按时间倒序）
     */
    List<DashboardHistory> listHistory(Long dashboardId);

    /**
     * 回滚到指定历史版本；snapshotCurrent 为真时先将当前配置存为历史版本，返回回滚后配置
     */
    Object rollback(Long dashboardId, Long historyId, boolean snapshotCurrent, String note);

    /**
     * 删除指定历史版本
     */
    boolean deleteHistory(Long dashboardId, Long historyId);
}
