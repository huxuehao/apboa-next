package com.hxh.apboa.node.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.template.TemplateFormatter;
import com.hxh.apboa.node.base.template.TemplateFormatterFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 描述：外部请求节点
 *
 * @author huxuehao
 **/
public class HttpExternalNode extends EnhancedNode {
    @Getter
    private final Config config;
    private final OkHttpClient client;
    private final Map<String, OkHttpClient> clientCache = new ConcurrentHashMap<>();
    private Map<String, Object> inputsMap;
    private final TemplateFormatter formatter;

    public HttpExternalNode(String id, String name, Config config) {
        super(id, name, NodeType.HTTP_EXTERNAL);
        this.config = config;
        this.formatter = TemplateFormatterFactory.createFormatter(config.getFormatterType());
        this.client = buildClient(config);
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        this.inputsMap = inputs;
        try {
            return successNodeOutput(output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(NodeOutput output) {

        if (config.isSyncExecute()) {
            // 同步执行
            HttpResponse httpResponse = executeSync(config.getRequest());

            // 请求响应失败
            if (!httpResponse.isSuccess()) {
                if (httpResponse.getError() != null) {
                    return executionNodeOutput(httpResponse.getError(), output);
                }
                if (httpResponse.getMessage() == null || httpResponse.getMessage().isEmpty()) {
                    throw new RuntimeException("请求状态码 " + httpResponse.getCode());
                }
                return executionNodeOutput(new RuntimeException(httpResponse.getMessage()), output);
            }

            String body = httpResponse.getBody();
            if (config.isBodyToObject()) {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, objectMapper.readTree(body));
                } catch (Exception e) {
                    throw new RuntimeException("JSON解析失败: " + e.getMessage());
                }
            } else {
                output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, body);
            }

            // 将 HTTP 请求及响应信息追加到执行上下文中
            output.addExecutionContext("method", config.getRequest().getMethod().name());
            output.addExecutionContext("url", config.getRequest().getUrl());
            output.addExecutionContext("statusCode", httpResponse.getCode());
            output.addExecutionContext("responseBody", body);
        } else {
            // 执行异步
            executeAsync(config.getRequest(), new HttpCallback() {
                @Override
                public void onSuccess(HttpResponse response) {}

                @Override
                public void onFailure(HttpResponse response) {
                    System.out.println(getName() + "执行失败: " + response.getMessage());
                }
            });
            output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, "ASYNC_EXECUTE");
        }

        output.markComplete();
        return output;
    }

    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed( getName() + "执行失败: " + e.getMessage());
        return output;
    }


    /**
     * 同步请求
     * @param request 请求配置
     */
    public HttpResponse executeSync(HttpRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            Request okRequest = buildRequest(request);
            try (Response response = client.newCall(okRequest).execute()) {
                String responseBody = response.body().string();
                long elapsedTime = System.currentTimeMillis() - startTime;

                if (response.isSuccessful()) {
                    return HttpResponse.success(response, responseBody, elapsedTime);
                } else {
                    return HttpResponse.failure(response.code(), response.message(), responseBody, elapsedTime);
                }
            }
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            return HttpResponse.failure(e, elapsedTime);
        }
    }

    /**
     * 异步请求
     * @param request 请求配置
     * @param callback 回调
     */
    public void executeAsync(HttpRequest request, HttpCallback callback) {
        long startTime = System.currentTimeMillis();
        try {
            Request okRequest = buildRequest(request);
            client.newCall(okRequest).enqueue(new Callback() {
                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    try {
                        String responseBody = response.body().string();
                        if (response.isSuccessful()) {
                            callback.onSuccess(HttpResponse.success(response, responseBody, elapsedTime));
                        } else {
                            callback.onFailure(HttpResponse.failure(response.code(), response.message(), responseBody, elapsedTime));
                        }
                    } catch (IOException e) {
                        callback.onFailure(HttpResponse.failure(e, elapsedTime));
                    }
                }

                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    callback.onFailure(HttpResponse.failure(e, elapsedTime));
                }
            });
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            callback.onFailure(HttpResponse.failure(e, elapsedTime));
        }
    }

    // 构建OkHttp请求
    private Request buildRequest(HttpRequest httpRequest) {
        // 处理路径参数
        String url = processPathParams(convertVariables(httpRequest.getUrl()), httpRequest.getPathParams());

        // 构建URL和查询参数
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (MapItem queryParam : httpRequest.getQueryParams()) {
            urlBuilder.addQueryParameter(queryParam.getKey(),
                    convertVariables(queryParam.getValue()));
        }

        // 构建请求体
        RequestBody requestBody = buildRequestBody(httpRequest);

        // 构建请求
        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build())
                .method(httpRequest.getMethod().name(), requestBody);

        // 添加请求头
        for (MapItem header : httpRequest.getHeaders()) {
            requestBuilder.addHeader(header.getKey(), convertVariables(header.getValue()));
        }

        // 设置Content-Type（如果未在headers中设置）
        if (requestBody != null && !hasKeyFromMapItem(httpRequest.getHeaders(), "Content-Type")) {
            String mediaType = httpRequest.getContentType().getMediaType();
            if (mediaType != null) {
                requestBuilder.addHeader("Content-Type", mediaType);
            }
        }

        return requestBuilder.build();
    }

    /**
     * 检查MapItem列表中是否存在指定键
     */
    private boolean hasKeyFromMapItem(List<MapItem> mapItems, String key) {
        for (MapItem mapItem : mapItems) {
            if (mapItem.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    // 处理路径参数
    private String processPathParams(String url, List<MapItem> pathParams) {
        String processedUrl = url;
        for (MapItem entry : pathParams) {
            String encodedValue = URLEncoder.encode(convertVariables(entry.getValue()), StandardCharsets.UTF_8);
            processedUrl = processedUrl.replace("{" + entry.getKey() + "}", encodedValue);
        }
        return processedUrl;
    }

    // 构建请求体
    private RequestBody buildRequestBody(HttpRequest httpRequest) {
        if (httpRequest.getBody() == null) {
            return null;
        }

        Object body = httpRequest.getBody();
        String mediaType = httpRequest.getContentType().getMediaType();

        switch (httpRequest.getContentType()) {
            case FORM_URLENCODED:
                if (body instanceof Map) {
                    FormBody.Builder formBuilder = new FormBody.Builder();
                    @SuppressWarnings("unchecked")
                    Map<String, String> formData = (Map<String, String>) body;
                    for (Map.Entry<String, String> entry : formData.entrySet()) {
                        formBuilder.add(entry.getKey(), convertVariables(entry.getValue()));
                    }
                    return formBuilder.build();
                }
                break;

            case FORM_DATA:
                if (body instanceof Map) {
                    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> formData = (Map<String, Object>) body;
                    for (Map.Entry<String, Object> entry : formData.entrySet()) {
                        if (entry.getValue() instanceof byte[]) {
                            multipartBuilder.addFormDataPart(
                                    entry.getKey(),
                                    "file",
                                    RequestBody.create((byte[]) entry.getValue(), MediaType.parse(mediaType))
                            );
                        } else {
                            multipartBuilder.addFormDataPart(
                                    entry.getKey(),
                                    convertVariables(entry.getValue().toString())
                            );
                        }
                    }
                    return multipartBuilder.build();
                }
                break;

            case JSON:
                if (body instanceof String) {
                    return RequestBody.create(convertVariables((String) body), MediaType.parse(mediaType));
                } else {
                    throw new IllegalArgumentException("不合法的 JSON 请求体");
                }

            default:
                if (body instanceof String) {
                    return RequestBody.create(convertVariables((String) body), MediaType.parse(mediaType));
                } else if (body instanceof byte[]) {
                     return RequestBody.create((byte[]) body, MediaType.parse(mediaType));
                }
        }

        // 默认处理为字符串
        return RequestBody.create(body.toString(), MediaType.parse(mediaType));
    }

    /**
     * 转换变量
     * @param template 模板
     * @return 模板内容
     */
    private String convertVariables(String template) {
        Object format = formatter.format(template, inputsMap, false);
        if (format == null) {
            return template;
        }
        return format.toString();
    }

    // 构建OkHttpClient
    private OkHttpClient buildClient(Config config) {
        String cacheKey = config.toString(); // 简化实现
        return clientCache.computeIfAbsent(cacheKey, k -> {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                    .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                    .followRedirects(config.isFollowRedirects())
                    .retryOnConnectionFailure(true);

            // 添加重试拦截器
            if (config.getMaxRetries() > 0) {
                builder.addInterceptor(new RetryInterceptor(config.getMaxRetries(), config.getRetryStatusCodes()));
            }

            return builder.build();
        });
    }

    // 重试拦截器
    private record RetryInterceptor(int maxRetries, List<Integer> retryStatusCodes) implements Interceptor {
        @NotNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response;
            IOException exception = null;

            for (int i = 0; i <= maxRetries; i++) {
                try {
                    response = chain.proceed(request);
                    if (response.isSuccessful()) {
                        return response;
                    }
                    // 对于不成功的响应，也考虑重试（可选）
                    if (i < maxRetries && shouldRetry(response.code())) {
                        response.close();
                        continue;
                    }
                    return response;
                } catch (IOException e) {
                    exception = e;
                    if (i == maxRetries) {
                        throw e;
                    }
                }
            }

            throw exception != null ? exception : new IOException("请求失败次数已超过 " + maxRetries + " 次");
        }

        private boolean shouldRetry(int code) {
            if (retryStatusCodes == null || retryStatusCodes.isEmpty()) {
                return code == 408 || // 请求超时
                        code == 429 || // 请求过多
                        code >= 500;   // 服务异常
            } else {
                return retryStatusCodes.contains(code);
            }
        }
    }


    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (config.getConnectTimeout() < 0) {
            return VerifyResult.invalid(new VerifyFail("connectTimeout", "连接超时时间不能小于0"));
        }
        if (config.getReadTimeout() < 0) {
            return VerifyResult.invalid(new VerifyFail("readTimeout", "读取超时时间不能小于0"));
        }
        if (config.getWriteTimeout() < 0) {
            return VerifyResult.invalid(new VerifyFail("writeTimeout", "写入超时时间不能小于0"));
        }
        if (config.getMaxRetries() > 10) {
            return VerifyResult.invalid(new VerifyFail("maxRetries", "最大重试次数不能大于10"));
        }

        if (config.getRequest() == null) {
            return VerifyResult.invalid(new VerifyFail("request", "请求配置不能为空"));
        } else if (config.getRequest().getUrl() == null || config.getRequest().getUrl().isEmpty()) {
            return VerifyResult.invalid(new VerifyFail("request.url", "请求URL不能为空"));
        } else if (config.getRequest().getMethod() == null) {
            return VerifyResult.invalid(new VerifyFail("request.method", "请求方法不能为空"));
        } else if (config.getRequest().getContentType() == null) {
            return VerifyResult.invalid(new VerifyFail("request.contentType", "请求内容类型不能为空"));
        }

        return VerifyResult.valid();
    }
}
