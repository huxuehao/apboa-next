package com.hxh.apboa.datasource.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.datasource.mapper.DatasourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：数据源服务实现
 *
 * @author huxuehao
 **/
@Service
public class DatasourceServiceImpl extends ServiceImpl<DatasourceMapper, Datasource> implements DatasourceService {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDatasource(Integer force, List<String> datasourceIds) {
        // TODO 需要判断有没有被使用
        if (force == 0) {
            throw new RuntimeException("请选择强制删除");
        }
        return this.removeByIds(datasourceIds);
    }

    @Override
    public boolean updateEnable(String datasourceId, Integer enable) {
        return lambdaUpdate()
                .set(Datasource::getEnabled, enable)
                .eq(Datasource::getId, datasourceId)
                .update();
    }
}
