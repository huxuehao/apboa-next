package com.hxh.apboa.dashboard.dataset.guard;

/**
 * 描述：数据集安全校验异常
 *
 * @author huxuehao
 **/
public class DatasetSecurityException extends RuntimeException {
    public DatasetSecurityException(String message) {
        super(message);
    }
}
