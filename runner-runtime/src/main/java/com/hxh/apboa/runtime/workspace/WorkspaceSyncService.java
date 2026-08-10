package com.hxh.apboa.runtime.workspace;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.ZipExtractUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 本地存储模式下的工作空间快照同步服务。
 *
 * @author huxuehao
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "apboa.storage-mode", havingValue = "local")
public class WorkspaceSyncService {

    private static final long DEBOUNCE_SECONDS = 30L;
    private final BlockingQueue<WorkspaceTask> queue = new LinkedBlockingQueue<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> debounceGenerations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> localFingerprints = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> localVersions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "workspace-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final String consoleUrl;
    private final String nodeId = SysConst.CURRENT_NODE_ID;

    public WorkspaceSyncService(
            RestTemplateBuilder builder,
            JdbcTemplate jdbcTemplate,
            @Value("${heartbeat.console-url:http://localhost:3060}") String consoleUrl) {
        this.restTemplate = builder.connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(120)).build();
        this.jdbcTemplate = jdbcTemplate;
        this.consoleUrl = consoleUrl;
    }

    @PostConstruct
    public void start() {
        worker.submit(this::consume);
        scheduler.schedule(this::initialSync, 5, TimeUnit.SECONDS);
    }

    /** 新节点启动后主动补齐控制台已有的工作空间快照。 */
    private void initialSync() {
        try {
            String token = jdbcTemplate.queryForObject("SELECT skill_token FROM skill_token LIMIT 1", String.class);
            String url = UriComponentsBuilder.fromUriString(consoleUrl)
                    .path("/internal/workspace/list").queryParam("token", token).toUriString();
            WorkspaceSyncMessage[] messages = restTemplate.getForObject(url, WorkspaceSyncMessage[].class);
            if (messages != null) {
                for (WorkspaceSyncMessage message : messages) {
                    if (message == null || message.tenantCode() == null || message.threadId() == null) continue;
                    String key = key(message.tenantCode(), message.threadId());
                    if (message.version() > localVersions.getOrDefault(key, 0L)) {
                        queue.offer(new WorkspaceTask(message.tenantCode(), message.threadId(), message.version(), message.sha256()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("启动时补齐工作空间失败，后续可通过重新启动节点重试: {}", e.getMessage());
        }
    }

    /** Agent 流结束后加入队列，同一个工作空间在防抖窗口内只保留最后一次。 */
    public void enqueue(String tenantCode, String threadId) {
        if (tenantCode == null || tenantCode.isBlank() || threadId == null || threadId.isBlank()) {
            return;
        }
        String key = key(tenantCode, threadId);
        long generation = debounceGenerations.merge(key, 1L, Long::sum);
        ScheduledFuture<?> previous = pending.remove(key);
        if (previous != null) {
            previous.cancel(false);
        }
        pending.put(key, scheduler.schedule(() -> {
            if (debounceGenerations.getOrDefault(key, 0L) == generation
                    && pending.remove(key) != null) {
                queue.offer(new WorkspaceTask(tenantCode, threadId));
            }
        }, DEBOUNCE_SECONDS, TimeUnit.SECONDS));
    }

    /**
     * 新一轮对话开始前取消同一工作空间尚未开始的本地快照同步。
     *
     * @param threadId 会话 ID
     */
    public void cancelPending(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        pending.keySet().stream()
                .filter(key -> key.endsWith(":" + threadId))
                .forEach(key -> {
                    debounceGenerations.merge(key, 1L, Long::sum);
                    ScheduledFuture<?> future = pending.remove(key);
                    if (future != null) {
                        future.cancel(false);
                    }
                });
        queue.removeIf(task -> task.version() == 0L && threadId.equals(task.threadId()));
    }

    /** 接收其他节点的快照通知。 */
    public void acceptRemote(WorkspaceSyncMessage message) {
        if (message == null || message.tenantCode() == null || message.threadId() == null
                || message.version() <= 0 || nodeId.equals(message.sourceNodeId())) {
            return;
        }
        String key = key(message.tenantCode(), message.threadId());
        long current = localVersions.getOrDefault(key, 0L);
        if (message.version() <= current) {
            return;
        }
        queue.offer(new WorkspaceTask(message.tenantCode(), message.threadId(), message.version(), message.sha256()));
    }

    private void consume() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WorkspaceTask task = queue.take();
                if (task.version() > 0) {
                    long current = localVersions.getOrDefault(key(task.tenantCode(), task.threadId()), 0L);
                    if (task.version() <= current) {
                        continue;
                    }
                    downloadAndApply(task);
                } else {
                    uploadSnapshot(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("工作空间同步失败", e);
            }
        }
    }

    private void uploadSnapshot(WorkspaceTask task) throws Exception {
        Path source = workspacePath(task.tenantCode(), task.threadId());
        if (!Files.isDirectory(source)) {
            return;
        }
        String workspaceKey = key(task.tenantCode(), task.threadId());
        String fingerprint = fingerprint(source);
        String previousFingerprint = getStoredFingerprint(task.tenantCode(), task.threadId(), workspaceKey);
        if (fingerprint.equals(previousFingerprint)) {
            log.debug("工作空间未发生变化，跳过同步: tenantCode={}, threadId={}",
                    task.tenantCode(), task.threadId());
            return;
        }
        Path archive = Files.createTempFile("workspace-sync-", ".zip");
        try {
            zip(source, archive);
            String sha256 = sha256(archive);
            String token = jdbcTemplate.queryForObject("SELECT skill_token FROM skill_token LIMIT 1", String.class);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("archive", new FileSystemResource(archive));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            String url = UriComponentsBuilder.fromUriString(consoleUrl)
                    .path("/internal/workspace/upload")
                    .queryParam("tenantCode", task.tenantCode())
                    .queryParam("threadId", task.threadId())
                    .queryParam("sourceNodeId", nodeId)
                    .queryParam("sha256", sha256)
                    .queryParam("token", token)
                    .toUriString();
            ResponseEntity<WorkspaceSyncMessage> response = restTemplate.postForEntity(url, request, WorkspaceSyncMessage.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IOException("控制台返回异常状态: " + response.getStatusCode());
            }
            localVersions.put(workspaceKey, response.getBody().version());
            storeFingerprint(task.tenantCode(), task.threadId(), workspaceKey, fingerprint);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private void downloadAndApply(WorkspaceTask task) throws Exception {
        String token = jdbcTemplate.queryForObject("SELECT skill_token FROM skill_token LIMIT 1", String.class);
        String url = UriComponentsBuilder.fromUriString(consoleUrl)
                .path("/internal/workspace/download")
                .queryParam("tenantCode", task.tenantCode())
                .queryParam("threadId", task.threadId())
                .queryParam("token", token)
                .toUriString();
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("控制台下载工作空间失败: " + response.getStatusCode());
        }
        Path tempZip = Files.createTempFile("workspace-remote-", ".zip");
        Files.write(tempZip, response.getBody());
        String expectedSha = response.getHeaders().getFirst("X-Apboa-Workspace-Sha256");
        if (expectedSha != null && !expectedSha.equalsIgnoreCase(sha256(tempZip))) {
            Files.deleteIfExists(tempZip);
            throw new IOException("工作空间快照校验失败");
        }
        Path target = workspacePath(task.tenantCode(), task.threadId());
        Files.createDirectories(target.getParent());
        Path extracted = target.resolveSibling(target.getFileName() + ".sync-" + nodeId);
        deleteDirectory(extracted);
        ZipExtractUtils.extractZipSafely(tempZip, extracted);
        deleteDirectory(target);
        try {
            Files.move(extracted, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(extracted, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(tempZip);
        String workspaceKey = key(task.tenantCode(), task.threadId());
        localVersions.put(workspaceKey, task.version());
        storeFingerprint(task.tenantCode(), task.threadId(), workspaceKey, fingerprint(target));
    }

    private Path workspacePath(String tenantCode, String threadId) {
        validatePart(tenantCode);
        validatePart(threadId);
        return Paths.get(SysConst.getWorkspacePath(tenantCode), threadId).toAbsolutePath().normalize();
    }

    private void validatePart(String value) {
        if (value == null || value.isBlank() || value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException("工作空间标识不合法");
        }
    }

    private String key(String tenantCode, String threadId) { return tenantCode + ":" + threadId; }

    /**
     * 计算工作空间目录的近似变化指纹
     * <br/>
     * 需要明确的是，这套方案是元数据近似判断，并非文件内容哈希。
     * 极端情况下，如果文件内容发生变化，但相对路径、类型、文件大小和最后修改时间都恢复成完全相同的值，系统可能将其判断为未变化。
     * 这是该方案为了避免每次同步读取全部文件内容而保留的边界。
     * <br/>
     * 另外，计算指纹失败后，会抛出异常，本次 workspace 的同步会取消！
     */
    private String fingerprint(Path workspaceDir) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.walk(workspaceDir)
                .filter(path -> !path.equals(workspaceDir))
                .sorted()) {
            stream.forEach(path -> {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    String relativePath = workspaceDir.relativize(path)
                            .toString().replace('\\', '/');
                    String entry = relativePath + "\u0000"
                            + (attributes.isDirectory() ? "directory" : "file") + "\u0000"
                            + attributes.size() + "\u0000"
                            + attributes.lastModifiedTime().toMillis() + "\n";
                    digest.update(entry.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
        } catch (CompletionException e) {
            throw new IOException("计算工作空间指纹失败", e.getCause());
        }
        return toHex(digest.digest());
    }

    private String getStoredFingerprint(String tenantCode, String threadId, String workspaceKey) {
        return localFingerprints.computeIfAbsent(workspaceKey, key -> {
            try {
                Path state = fingerprintStatePath(tenantCode, threadId);
                return Files.exists(state) ? Files.readString(state).trim() : null;
            } catch (IOException e) {
                log.warn("读取工作空间指纹失败: tenantCode={}, threadId={}, error={}",
                        tenantCode, threadId, e.getMessage());
                return null;
            }
        });
    }

    private void storeFingerprint(String tenantCode, String threadId, String workspaceKey,
                                  String fingerprint) throws IOException {
        Path state = fingerprintStatePath(tenantCode, threadId);
        Files.createDirectories(state.getParent());
        Path temporary = Files.createTempFile(state.getParent(), "workspace-", ".fingerprint.tmp");
        try {
            Files.writeString(temporary, fingerprint, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, state, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, state, StandardCopyOption.REPLACE_EXISTING);
            }
            localFingerprints.put(workspaceKey, fingerprint);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path fingerprintStatePath(String tenantCode, String threadId) {
        validatePart(tenantCode);
        validatePart(threadId);
        return Paths.get(SysConst.ROOT_DIR_NAME, "workspace-sync-state", tenantCode,
                threadId + ".fingerprint").toAbsolutePath().normalize();
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private void zip(Path source, Path target) throws IOException {
        try (OutputStream output = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(output)) {
            try (var stream = Files.walk(source)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        zip.putNextEntry(new ZipEntry(source.relativize(path).toString().replace('\\', '/')));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
            }
        } catch (CompletionException e) {
            throw new IOException("压缩工作空间失败", e.getCause());
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try { Files.deleteIfExists(item); } catch (IOException e) { throw new CompletionException(e); }
            });
        } catch (CompletionException e) {
            throw new IOException("清理工作空间临时目录失败", e.getCause());
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    public record WorkspaceTask(String tenantCode, String threadId, long version, String sha256) {
        public WorkspaceTask(String tenantCode, String threadId) { this(tenantCode, threadId, 0L, null); }
    }

    public record WorkspaceSyncMessage(String tenantCode, String threadId, String sourceNodeId,
                                       long version, String sha256, long changedAt) { }
}
