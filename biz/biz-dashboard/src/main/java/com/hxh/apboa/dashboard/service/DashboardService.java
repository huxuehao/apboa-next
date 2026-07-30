package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.Dashboard;
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
     * 恢复默认（删除个人副本回退到模板）
     */
    boolean resetPersonal(Long dashboardId);
}
