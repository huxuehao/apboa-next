package com.hxh.apboa.dashboard.dataset.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.enums.dashboard.DatasetType;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.dashboard.config.DashboardDatasetProperties;
import com.hxh.apboa.dashboard.dataset.guard.HttpUrlGuard;
import com.hxh.apboa.dashboard.dataset.model.ColumnMeta;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteCommand;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import com.hxh.apboa.dashboard.dataset.model.HttpDatasetConfig;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：HTTP GET 接口型数据集执行器。后端代理执行，支持 query 模板参数、固定请求头、
 * 同源自动携带平台 token、SSRF/超时/大小防护，并将响应 JSON 映射为列/行结果。
 *
 * @author huxuehao
 **/
@Component
public class HttpDatasetExecutor implements DatasetExecutor {
    private final HttpUrlGuard urlGuard;
    private final DashboardDatasetProperties properties;
    private final HttpClient httpClient;

    public HttpDatasetExecutor(HttpUrlGuard urlGuard, DashboardDatasetProperties properties) {
        this.urlGuard = urlGuard;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getHttpConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public boolean supports(DatasetType type) {
        return type == DatasetType.HTTP;
    }

    @Override
    public DatasetExecuteResult execute(DatasetExecuteCommand command) {
        long start = System.currentTimeMillis();
        HttpDatasetConfig config = JsonUtils.objectToBean(command.getHttpConfig(), HttpDatasetConfig.class);
        if (config == null || config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP 数据集未配置请求地址");
        }
        Map<String, Object> params = DatasetParamSupport.mergeParams(command.getParams());

        String fullUrl = buildUrl(config, params);
        boolean sameOrigin = isSameOrigin(fullUrl, command.getCallerOrigin());
        urlGuard.validate(fullUrl, sameOrigin);

        String body = send(fullUrl, config, command, sameOrigin);
        DatasetExecuteResult result = mapResult(body, config.getDataPath(), command.getLimit());
        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    /** 拼接 URL：解析 query 值模板（:name 取参数、未命中回退默认、仍空则省略），URL 编码 */
    private String buildUrl(HttpDatasetConfig config, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder(config.getUrl().trim());
        boolean hasQuery = config.getUrl().contains("?");
        if (config.getQueries() != null) {
            for (HttpDatasetConfig.HttpParam q : config.getQueries()) {
                if (q.getKey() == null || q.getKey().isBlank()) {
                    continue;
                }
                String value = resolveValue(q.getValue(), q.getDefaultValue(), params);
                if (value == null) {
                    continue;
                }
                sb.append(hasQuery ? '&' : '?');
                sb.append(encode(q.getKey())).append('=').append(encode(value));
                hasQuery = true;
            }
        }
        return sb.toString();
    }

    /** 解析单个 query 值：:name -> 参数值；null/缺省 -> 默认值；仍为空 -> null（省略该参数） */
    private String resolveValue(String template, String defaultValue, Map<String, Object> params) {
        String resolved = null;
        if (template != null && template.startsWith(":")) {
            Object v = params.get(template.substring(1));
            resolved = v == null ? null : String.valueOf(v);
        } else if (template != null && !template.isBlank()) {
            resolved = template;
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = defaultValue;
        }
        return (resolved == null || resolved.isBlank()) ? null : resolved;
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 发起 GET 请求：固定头 + 同源转发 Authorization + 超时；返回响应体 */
    private String send(String fullUrl, HttpDatasetConfig config, DatasetExecuteCommand command, boolean sameOrigin) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofMillis(properties.getHttpReadTimeoutMs()))
                .GET();
        if (config.getHeaders() != null) {
            for (HttpDatasetConfig.HttpHeader h : config.getHeaders()) {
                if (h.getKey() != null && !h.getKey().isBlank() && h.getValue() != null) {
                    builder.header(h.getKey(), h.getValue());
                }
            }
        }
        // 同源自动携带平台 token（跨域绝不携带）
        if (sameOrigin && command.getCallerToken() != null && !command.getCallerToken().isBlank()) {
            builder.header("Authorization", command.getCallerToken());
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("接口返回非成功状态: " + response.statusCode());
            }
            String responseBody = response.body();
            if (responseBody != null
                    && responseBody.getBytes(StandardCharsets.UTF_8).length > properties.getHttpMaxResponseBytes()) {
                throw new RuntimeException("接口响应体超过大小上限");
            }
            return responseBody;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("HTTP 数据集请求失败: " + e.getMessage(), e);
        }
    }

    /** 比较目标 URL 的 scheme://host:port 与调用方 Origin 是否一致 */
    private boolean isSameOrigin(String fullUrl, String callerOrigin) {
        if (callerOrigin == null || callerOrigin.isBlank()) {
            return false;
        }
        try {
            URI target = URI.create(fullUrl);
            URI origin = URI.create(callerOrigin.trim());
            return equalsIgnoreCase(target.getScheme(), origin.getScheme())
                    && equalsIgnoreCase(target.getHost(), origin.getHost())
                    && normalizePort(target) == normalizePort(origin);
        } catch (Exception e) {
            return false;
        }
    }

    private int normalizePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    /** 响应 JSON -> 列/行结果：dataPath 定位数组，数组->多行、对象->单行，首行推断列 */
    private DatasetExecuteResult mapResult(String body, String dataPath, int limit) {
        JsonNode root = JsonUtils.parse(body);
        if (root == null) {
            throw new RuntimeException("接口响应不是有效 JSON");
        }
        JsonNode located = locate(root, dataPath);
        int effectiveLimit = limit <= 0 ? properties.getQueryLimit() : Math.min(limit, properties.getMaxRows());

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        if (located != null && located.isArray()) {
            for (JsonNode item : located) {
                if (rows.size() >= effectiveLimit) {
                    truncated = true;
                    break;
                }
                rows.add(toRow(item));
            }
        } else if (located != null && !located.isNull()) {
            rows.add(toRow(located));
        }

        List<ColumnMeta> columns = inferColumns(rows);
        DatasetExecuteResult result = new DatasetExecuteResult();
        result.setColumns(columns);
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setTruncated(truncated);
        return result;
    }

    /** 按点路径定位节点，路径为空返回根 */
    private JsonNode locate(JsonNode root, String dataPath) {
        if (dataPath == null || dataPath.isBlank()) {
            return root;
        }
        JsonNode node = root;
        for (String seg : dataPath.trim().split("\\.")) {
            if (node == null) {
                return null;
            }
            node = node.get(seg);
        }
        return node;
    }

    /** 单个 JSON 节点转一行：对象取字段，非对象放入 value 列 */
    private Map<String, Object> toRow(JsonNode node) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                row.put(e.getKey(), toValue(e.getValue()));
            }
        } else {
            row.put("value", toValue(node));
        }
        return row;
    }

    /** JSON 值转 Java 标量/结构 */
    private Object toValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.toString();
    }

    /** 由首行键推断列 */
    private List<ColumnMeta> inferColumns(List<Map<String, Object>> rows) {
        List<ColumnMeta> columns = new ArrayList<>();
        if (rows.isEmpty()) {
            return columns;
        }
        for (Map.Entry<String, Object> e : rows.get(0).entrySet()) {
            String type = e.getValue() == null ? "string" : e.getValue().getClass().getSimpleName();
            columns.add(new ColumnMeta(e.getKey(), type));
        }
        return columns;
    }
}
