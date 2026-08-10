package com.hxh.apboa.console.workspace;

import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.config.auth.PassAuth;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * 工作空间快照内部同步接口，仅供 runner-runtime 使用。
 *
 * @author huxuehao
 */
@Slf4j
@RestController
@RequestMapping("/internal/workspace")
public class WorkspaceSyncInternalController {

    private static final Path ROOT = Paths.get(".apboa", "workspace-sync").toAbsolutePath().normalize();
    private final JdbcTemplate jdbcTemplate;
    private final MessagePublisher messagePublisher;

    public WorkspaceSyncInternalController(JdbcTemplate jdbcTemplate, MessagePublisher messagePublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.messagePublisher = messagePublisher;
    }

    @PassAuth
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public synchronized WorkspaceSyncMessage upload(
            @RequestParam String tenantCode,
            @RequestParam String threadId,
            @RequestParam String sourceNodeId,
            @RequestParam String sha256,
            @RequestParam String token,
            @RequestPart("archive") MultipartFile archive) throws IOException {
        checkToken(token);
        validatePart(tenantCode);
        validatePart(threadId);
        validatePart(sourceNodeId);
        if (archive == null || archive.isEmpty()) {
            throw new BusinessException("工作空间快照不能为空");
        }
        Path directory = ROOT.resolve(tenantCode).normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(threadId + ".zip").normalize();
        Path metadata = directory.resolve(threadId + ".json").normalize();
        if (!target.startsWith(directory) || !metadata.startsWith(directory)) {
            throw new BusinessException("工作空间路径不合法");
        }
        Path temp = Files.createTempFile(directory, threadId + "-", ".tmp");
        try {
            archive.transferTo(temp);
            String actualSha = sha256(temp);
            if (!actualSha.equalsIgnoreCase(sha256)) {
                throw new BusinessException("工作空间快照校验失败");
            }
            long version = readVersion(metadata) + 1;
            WorkspaceSyncMessage message = new WorkspaceSyncMessage(
                    tenantCode, threadId, sourceNodeId, version, actualSha, System.currentTimeMillis());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Path metadataTemp = Files.createTempFile(directory, threadId + "-", ".json.tmp");
            try {
                Files.writeString(metadataTemp, JsonUtils.toJsonStr(message), StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(metadataTemp, metadata, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(metadataTemp, metadata, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(metadataTemp);
            }
            messagePublisher.publish(RedisChannelTopic.WORKSPACE_SYNC_CHANNEL, JsonUtils.toJsonStr(message));
            return message;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @PassAuth
    @GetMapping("/download")
    public void download(@RequestParam String tenantCode,
                         @RequestParam String threadId,
                         @RequestParam String token,
                         HttpServletResponse response) throws IOException {
        checkToken(token);
        Path archive = archivePath(tenantCode, threadId);
        if (!Files.exists(archive)) {
            throw new BusinessException("工作空间快照不存在");
        }
        WorkspaceSyncMessage message = readMetadata(metadataPath(tenantCode, threadId));
        if (message == null) {
            throw new BusinessException("工作空间快照元数据不存在");
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("X-Apboa-Workspace-Version", String.valueOf(message.version()));
        response.setHeader("X-Apboa-Workspace-Sha256", message.sha256());
        try (OutputStream output = response.getOutputStream()) {
            Files.copy(archive, output);
        }
    }

    @PassAuth
    @GetMapping("/list")
    public List<WorkspaceSyncMessage> list(@RequestParam String token) throws IOException {
        checkToken(token);
        if (!Files.exists(ROOT)) return List.of();
        try (Stream<Path> paths = Files.walk(ROOT)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> readMetadataUnchecked(path))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private void checkToken(String token) {
        String internalToken = jdbcTemplate.queryForObject(
                "SELECT skill_token FROM " + TableConst.SKILL_TOKEN + " LIMIT 1", String.class);
        if (internalToken == null || !internalToken.equals(token)) {
            throw new BusinessException("无效的内部服务令牌");
        }
    }

    private Path archivePath(String tenantCode, String threadId) {
        validatePart(tenantCode);
        validatePart(threadId);
        return ROOT.resolve(tenantCode).resolve(threadId + ".zip").normalize();
    }

    private Path metadataPath(String tenantCode, String threadId) {
        return ROOT.resolve(tenantCode).resolve(threadId + ".json").normalize();
    }

    private long readVersion(Path metadata) {
        if (!Files.exists(metadata)) return 0L;
        WorkspaceSyncMessage message = readMetadata(metadata);
        return message == null ? 0L : message.version();
    }

    private WorkspaceSyncMessage readMetadata(Path path) {
        try {
            return JsonUtils.parse(Files.readString(path), WorkspaceSyncMessage.class);
        } catch (Exception e) {
            log.warn("读取工作空间同步元数据失败: {}", path);
            return null;
        }
    }

    private WorkspaceSyncMessage readMetadataUnchecked(Path path) {
        return readMetadata(path);
    }

    private void validatePart(String value) {
        if (value == null || value.isBlank() || value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new BusinessException("工作空间标识不合法");
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception e) {
            throw new IOException("计算工作空间快照摘要失败", e);
        }
    }

    public record WorkspaceSyncMessage(String tenantCode, String threadId, String sourceNodeId,
                                       long version, String sha256, long changedAt) { }
}
