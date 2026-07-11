package com.hxh.apboa.node.base.request;

import lombok.Builder;
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
@Builder
public class RequestParams {
    private List<ParamItem> params;
}
