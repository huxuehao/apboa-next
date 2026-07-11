package com.hxh.apboa.common.enums;

/**
 * 描述：工作流运行状态
 *
 * @author huxuehao
 **/
public enum WorkflowRunStatus {
    RUNNING("运行中"),
    SUCCESS("运行成功"),
    FAIL("运行失败"),
    STOP("停止");
    WorkflowRunStatus(String msg){}
}
