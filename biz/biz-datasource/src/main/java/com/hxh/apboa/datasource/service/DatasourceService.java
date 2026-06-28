package com.hxh.apboa.datasource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.Datasource;

import java.util.List;

public interface DatasourceService extends IService<Datasource> {
    boolean deleteDatasource(Integer force, List<String> datasourceIds);

    boolean updateEnable(String datasourceId, Integer enable);

    boolean checkConnect(Datasource datasource);

    List<Datasource> listByEnabled(Integer enabled);
}
