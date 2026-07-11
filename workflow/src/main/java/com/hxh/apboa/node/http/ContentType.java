package com.hxh.apboa.node.http;

import lombok.Getter;

/**
 * 描述：
 *
 * @author huxuehao
 **/
@Getter
public enum ContentType {
    JSON("application/json; charset=utf-8"),
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    FORM_DATA("multipart/form-data"),
    XML("application/xml"),
    TEXT_PLAIN("text/plain"),
    OCTET_STREAM("application/octet-stream");

    private final String mediaType;

    ContentType(String mediaType) {
        this.mediaType = mediaType;
    }

}
