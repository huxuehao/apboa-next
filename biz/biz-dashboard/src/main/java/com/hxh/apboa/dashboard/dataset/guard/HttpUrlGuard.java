package com.hxh.apboa.dashboard.dataset.guard;

import com.hxh.apboa.dashboard.config.DashboardDatasetProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 描述：HTTP 数据集 URL 安全校验（SSRF 基础防护）。
 * 仅允许 http/https；禁止环回、内网、link-local、通配及云元数据地址；可选主机白名单。
 *
 * @author huxuehao
 **/
@Component
public class HttpUrlGuard {
    private final DashboardDatasetProperties properties;

    public HttpUrlGuard(DashboardDatasetProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并返回规范化 URI。非法时抛出异常。
     *
     * @param sameOrigin 是否与调用方同源（同源为平台自身可信，跳过内网/白名单限制）
     */
    public URI validate(String url, boolean sameOrigin) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTTP 数据集 URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("HTTP 数据集 URL 非法: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("HTTP 数据集仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("HTTP 数据集 URL 缺少主机");
        }
        // 同源即平台自身，可信，不受内网/白名单限制
        if (sameOrigin) {
            return uri;
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        checkAllowedHosts(lowerHost);
        checkInternalAddress(lowerHost);
        return uri;
    }

    /** 白名单校验（配置了才启用） */
    private void checkAllowedHosts(String host) {
        if (properties.getHttpAllowedHosts() == null || properties.getHttpAllowedHosts().isEmpty()) {
            return;
        }
        boolean allowed = properties.getHttpAllowedHosts().stream()
                .anyMatch(h -> host.equals(h.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new IllegalArgumentException("目标主机不在白名单内: " + host);
        }
    }

    /** 禁止访问内网/环回/link-local/云元数据地址 */
    private void checkInternalAddress(String host) {
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new IllegalArgumentException("禁止访问内网/环回地址: " + host);
                }
                // 云元数据地址（169.254.169.254 属 link-local，已被上面拦截，这里冗余保护）
                if ("169.254.169.254".equals(addr.getHostAddress())) {
                    throw new IllegalArgumentException("禁止访问云元数据地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("目标主机无法解析: " + host);
        }
    }
}
