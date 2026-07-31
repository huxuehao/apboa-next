package com.hxh.apboa.dashboard.dataset.executor;

import com.hxh.apboa.common.enums.dashboard.DatasetType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 描述：数据集执行器工厂。按数据集类型选择匹配的执行器实现。
 *
 * @author huxuehao
 **/
@Component
public class DatasetExecutorFactory {
    private final List<DatasetExecutor> executors;

    public DatasetExecutorFactory(List<DatasetExecutor> executors) {
        this.executors = executors;
    }

    public DatasetExecutor resolve(DatasetType type) {
        return executors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到匹配的数据集执行器: type=" + type));
    }
}
