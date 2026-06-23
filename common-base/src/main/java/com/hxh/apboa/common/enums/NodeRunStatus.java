package com.hxh.apboa.common.enums;

/**
 * 描述：节点运行状态
 *
 * @author huxuehao
 **/
public enum NodeRunStatus {
    RUNNING("运行中"),
    SUCCESS("运行成功"),
    FAIL("运行失败"),
    STOP("停止");
    NodeRunStatus(String msg){}
}
