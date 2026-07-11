package com.hxh.apboa.workflow.core;

import com.hxh.apboa.node.start.Param;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：请求参数
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class RequestParams {
    private List<Param> params;
    private byte[] body;
}
