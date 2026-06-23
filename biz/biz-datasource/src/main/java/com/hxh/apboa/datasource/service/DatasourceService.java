package com.hxh.apboa.datasource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.Datasource;

import java.util.List;

/**
 * 描述：数据源服务
 *
 * @author huxuehao
 **/
public interface DatasourceService extends IService<Datasource> {
    /**
     * 删除数据源
     *
     * @param datasourceIds 数据源ID
     * @return 是否添加成功
     */
    boolean deleteDatasource(Integer force, List<String> datasourceIds);

    /**
     * 更新数据源是否可以
     *
     * @param datasourceId 数据源ID
     * @param enable       状态值
     * @return 是否删除成功
     */
    boolean updateEnable(String datasourceId, Integer enable);
}
