package com.hxh.apboa.runtime.endpoint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.entity.Attach;
import com.hxh.apboa.common.entity.KnowledgeBaseConfig;
import com.hxh.apboa.common.entity.RagDocument;
import com.hxh.apboa.common.entity.RagDocumentChunk;
import com.hxh.apboa.common.enums.KbType;
import com.hxh.apboa.common.enums.RagDocumentStatus;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.knowledge.IKnowledge;
import com.hxh.apboa.engine.knowledge.KnowledgeFactory;
import com.hxh.apboa.engine.rag.DocumentParser;
import com.hxh.apboa.engine.rag.mapper.RagDocumentChunkMapper;
import com.hxh.apboa.engine.rag.mapper.RagDocumentMapper;
import com.hxh.apboa.engine.rag.service.LocalRagService;
import com.hxh.apboa.knowledge.service.KnowledgeBaseConfigService;
import com.hxh.apboa.resource.service.AttachService;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG文档管理Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/runtime/rag/document")
@RequiredArgsConstructor
public class RagEndPoint {

    private final LocalRagService localRagService;
    private final DocumentParser documentParser;
    private final RagDocumentMapper ragDocumentMapper;
    private final RagDocumentChunkMapper ragDocumentChunkMapper;
    private final KnowledgeBaseConfigService knowledgeBaseConfigService;
    private final AttachService attachService;

    /**
     * 上传文档到指定知识库
     */
    @PostMapping("/upload")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Long> upload(@RequestParam("file") MultipartFile file,
                          @RequestParam("knowledgeBaseConfigId") Long kbConfigId) {
        KnowledgeBaseConfig kbConfig = knowledgeBaseConfigService.getById(kbConfigId);
        if (kbConfig == null) {
            return R.fail("知识库配置不存在");
        }
        if (kbConfig.getKbType() != KbType.LOCAL) {
            return R.fail("仅支持本地类型知识库的文档上传");
        }

        try {
            String fileName = file.getOriginalFilename();
            if (documentParser.isNotSupported(fileName)) {
                return R.fail("不支持的文件类型，支持的格式: txt、md、pdf、doc、docx、xlsx、xls、csv、pptx、ppt");
            }

            Attach attach = attachService.upload(file, fileName);
            String fileType = extractFileType(fileName);

            RagDocument document = RagDocument.builder()
                    .id(IdWorker.getId())
                    .knowledgeBaseConfigId(kbConfigId)
                    .fileName(fileName)
                    .filePath(String.valueOf(attach.getId()))
                    .fileSize(file.getSize())
                    .fileType(fileType)
                    .chunkCount(0)
                    .status(RagDocumentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            ragDocumentMapper.insert(document);

            // 异步处理文档，从已保存的附件中读取文件流
            Long tenantId = TenantUtils.getCurrentTenantId();
            String tenantCode = TenantUtils.getCurrentTenantCode();
            localRagService.reprocessDocument(document, attach, kbConfig, tenantId, tenantCode);

            return R.data(document.getId());
        } catch (Exception e) {
            return R.fail("文档上传处理失败: " + e.getMessage());
        }
    }

    /**
     * 查询知识库下的文档列表
     */
    @GetMapping("/list")
    public R<List<RagDocument>> list(@RequestParam("knowledgeBaseConfigId") Long kbConfigId) {
        LambdaQueryWrapper<RagDocument> wrapper = new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getKnowledgeBaseConfigId, kbConfigId)
                .orderByDesc(RagDocument::getCreatedAt);
        List<RagDocument> documents = ragDocumentMapper.selectList(wrapper);
        return R.data(documents);
    }

    /**
     * 删除文档
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            localRagService.deleteDocument(id);
        }
        return R.data(true);
    }

    /**
     * 查询文档分块列表
     */
    @GetMapping("/chunks")
    public R<List<com.hxh.apboa.common.entity.RagDocumentChunk>> chunks(
            @RequestParam("documentId") Long documentId) {
        LambdaQueryWrapper<RagDocumentChunk> chunkWrapper = new LambdaQueryWrapper<RagDocumentChunk>()
                .eq(RagDocumentChunk::getDocumentId, documentId)
                .orderByAsc(RagDocumentChunk::getChunkIndex);
        return R.data(ragDocumentChunkMapper.selectList(chunkWrapper));
    }

    /**
     * 更新分块内容
     */
    @PutMapping("/chunk/{id}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> updateChunk(@PathVariable("id") Long chunkId,
                                   @RequestBody Map<String, String> params) {
        String content = params.get("content");
        if (content == null || content.isBlank()) {
            return R.fail("分块内容不能为空");
        }
        try {
            localRagService.updateChunk(chunkId, content);
            return R.data(true);
        } catch (Exception e) {
            return R.fail("更新分块失败: " + e.getMessage());
        }
    }

    /**
     * 删除分块
     */
    @DeleteMapping("/chunk/{id}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> deleteChunk(@PathVariable("id") Long chunkId) {
        try {
            localRagService.deleteChunk(chunkId);
            return R.data(true);
        } catch (Exception e) {
            return R.fail("删除分块失败: " + e.getMessage());
        }
    }

    /**
     * RAG检索测试（支持所有类型知识库）
     *
     * <p>统一走 KnowledgeFactory → Knowledge.retrieve 通路（参考 WorkflowKnowledgeNodeExecutor），
     * 测试面板的高级参数通过 retrievalConfig 覆盖项合并到知识库检索配置上，仅本次测试生效、不落库。
     */
    @PostMapping("/search")
    public R<List<Map<String, Object>>> search(@RequestBody Map<String, Object> params, @RequestParam("kbType") KbType kbType) {
        if (params.get("knowledgeBaseConfigId") == null) {
            return R.fail("知识库配置ID不能为空");
        }
        Long kbConfigId = Long.valueOf(params.get("knowledgeBaseConfigId").toString());
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return R.fail("检索关键词不能为空");
        }

        KnowledgeBaseConfig kbConfig = knowledgeBaseConfigService.getById(kbConfigId);
        if (kbConfig == null) {
            return R.fail("知识库配置不存在");
        }
        if (kbConfig.getKbType() != kbType) {
            return R.fail("知识库类型与配置不一致");
        }

        IKnowledge iKnowledge = KnowledgeFactory.getKnowledge(kbType);
        if (iKnowledge == null) {
            return R.fail("知识库类型未注册: " + kbType);
        }

        try {
            // 将本次测试的高级参数覆盖项合并到检索配置（仅本次生效，不落库）
            JsonNode mergedRetrievalConfig = mergeRetrievalConfig(
                    kbConfig.getRetrievalConfig(), JsonUtils.valueToTree(params.get("retrievalConfig")));
            kbConfig.setRetrievalConfig(mergedRetrievalConfig);

            Knowledge knowledge = iKnowledge.build(kbConfig);
            RetrieveConfig retrieveConfig = buildRetrieveConfig(kbType, mergedRetrievalConfig);

            List<Document> documents = knowledge.retrieve(query, retrieveConfig).block();
            if (documents == null) {
                documents = List.of();
            }

            List<Map<String, Object>> results = documents.stream().map(doc -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", doc.getId());
                map.put("docId", doc.getMetadata().getDocId());
                map.put("chunkId", doc.getMetadata().getChunkId());
                map.put("content", doc.getMetadata().getContentText());
                map.put("score", doc.getScore());
                map.put("fileName", resolveFileName(doc));
                map.put("chunkIndex", resolvePayloadValue(doc, "chunkIndex", "position"));
                map.put("tokenCount", resolvePayloadValue(doc, "tokenCount", "tokens"));
                map.put("payload", doc.getPayload());
                return map;
            }).toList();

            return R.data(results);
        } catch (Exception e) {
            return R.fail("检索失败: " + e.getMessage());
        }
    }

    /**
     * 从检索结果中解析文档名（按各类型 payload 字段自适应：
     * 本地 -> fileName，Dify -> document.name，RagFlow -> document_name）
     */
    private Object resolveFileName(Document doc) {
        Object fileName = doc.getPayloadValue("fileName");
        if (fileName != null) {
            return fileName;
        }
        Object document = doc.getPayloadValue("document");
        if (document instanceof Map<?, ?> documentInfo) {
            Object name = documentInfo.get("name");
            if (name != null) {
                return name;
            }
        }
        return doc.getPayloadValue("document_name");
    }

    /**
     * 从检索结果 payload 中按顺序取第一个存在的字段值
     */
    private Object resolvePayloadValue(Document doc, String... keys) {
        for (String key : keys) {
            Object value = doc.getPayloadValue(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将本次测试的高级参数覆盖项浅合并到知识库检索配置之上，覆盖项为空时直接返回基础配置。
     */
    private JsonNode mergeRetrievalConfig(JsonNode base, JsonNode override) {
        if (override == null || override.isNull() || !override.isObject() || override.isEmpty()) {
            return base;
        }
        if (base == null || base.isNull() || !base.isObject()) {
            return override;
        }
        ObjectNode merged = base.deepCopy();
        override.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        return merged;
    }

    /**
     * 根据知识库类型从合并后的检索配置中构建 RetrieveConfig。
     */
    private RetrieveConfig buildRetrieveConfig(KbType kbType, JsonNode mergedRetrievalConfig) {
        int defaultLimit;
        String thresholdKey;
        double defaultThreshold;
        switch (kbType) {
            case DIFY -> {
                defaultLimit = 10;
                thresholdKey = "scoreThreshold";
                defaultThreshold = 0.0;
            }
            case RAGFLOW -> {
                defaultLimit = 1024;
                thresholdKey = "similarityThreshold";
                defaultThreshold = 0.2;
            }
            case LOCAL -> {
                defaultLimit = 5;
                thresholdKey = "scoreThreshold";
                defaultThreshold = 0.5;
            }
            default -> {
                defaultLimit = 5;
                thresholdKey = "scoreThreshold";
                defaultThreshold = 0.0;
            }
        }

        int limit = JsonUtils.getIntValue(mergedRetrievalConfig, "topK", defaultLimit);
        double scoreThreshold = JsonUtils.getDoubleValue(mergedRetrievalConfig, thresholdKey, defaultThreshold);

        return RetrieveConfig.builder()
                .limit(Math.max(limit, 1))
                .scoreThreshold(clampScore(scoreThreshold))
                .build();
    }

    /**
     * 将分数阈值限制在 [0, 1] 区间，避免 RetrieveConfig 构建失败。
     */
    private double clampScore(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }



    /**
     * 下载文档原始文件
     */
    @GetMapping("/download/{id}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public void download(@PathVariable("id") Long id, HttpServletResponse response) {
        RagDocument document = ragDocumentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }
        try {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment;filename=" + URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8));
            Attach attach = attachService.getById(Long.valueOf(document.getFilePath()));
            if (attach == null) {
                throw new RuntimeException("文件附件不存在");
            }
            try (OutputStream outputStream = response.getOutputStream()) {
                attachService.download(attach, outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 重新上传文档（替换原有文件并重新处理）
     */
    @PostMapping("/re-upload/{id}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> reUpload(@PathVariable("id") Long id,
                               @RequestParam("file") MultipartFile file) {
        RagDocument document = ragDocumentMapper.selectById(id);
        if (document == null) {
            return R.fail("文档不存在");
        }

        KnowledgeBaseConfig kbConfig = knowledgeBaseConfigService.getById(document.getKnowledgeBaseConfigId());
        if (kbConfig == null) {
            return R.fail("知识库配置不存在");
        }

        try {
            String fileName = file.getOriginalFilename();
            if (documentParser.isNotSupported(fileName)) {
                return R.fail("不支持的文件类型");
            }

            // 删除旧的向量和分块数据
            localRagService.deleteDocumentChunksAndVectors(id);

            // 删除旧附件并上传新附件
            Attach oldAttach = attachService.getById(Long.valueOf(document.getFilePath()));
            if (oldAttach != null) {
                attachService.removeById(oldAttach.getId());
            }

            Attach newAttach = attachService.upload(file, fileName);

            // 更新文档记录
            document.setFileName(fileName);
            document.setFilePath(String.valueOf(newAttach.getId()));
            document.setFileSize(file.getSize());
            document.setFileType(extractFileType(fileName));
            document.setChunkCount(0);
            document.setStatus(RagDocumentStatus.PENDING);
            document.setErrorMessage(null);
            document.setUpdatedAt(LocalDateTime.now());
            ragDocumentMapper.updateById(document);

            Long tenantId = TenantUtils.getCurrentTenantId();
            String tenantCode = TenantUtils.getCurrentTenantCode();
            // 异步重新处理文档，从已保存的附件中读取文件流
            localRagService.reprocessDocument(document, newAttach, kbConfig, tenantId, tenantCode);

            return R.data(true);
        } catch (Exception e) {
            return R.fail("重新上传处理失败: " + e.getMessage());
        }
    }

    /**
     * 重新分块处理（使用当前知识库配置重新解析和向量化）
     */
    @PostMapping("/re-chunk/{id}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> reChunk(@PathVariable("id") Long id) {
        RagDocument document = ragDocumentMapper.selectById(id);
        if (document == null) {
            return R.fail("文档不存在");
        }

        KnowledgeBaseConfig kbConfig = knowledgeBaseConfigService.getById(document.getKnowledgeBaseConfigId());
        if (kbConfig == null) {
            return R.fail("知识库配置不存在");
        }

        try {
            // 删除旧的向量和分块数据
            localRagService.deleteDocumentChunksAndVectors(id);

            // 通过附件服务获取文件流并重新处理
            Attach attach = attachService.getById(Long.valueOf(document.getFilePath()));
            if (attach == null) {
                return R.fail("文件附件不存在，请重新上传");
            }

            document.setChunkCount(0);
            document.setStatus(RagDocumentStatus.PROCESSING);
            document.setErrorMessage(null);
            document.setUpdatedAt(LocalDateTime.now());
            ragDocumentMapper.updateById(document);

            Long tenantId = TenantUtils.getCurrentTenantId();
            String tenantCode = TenantUtils.getCurrentTenantCode();
            // 异步重新处理文档
            localRagService.reprocessDocument(document, attach, kbConfig, tenantId, tenantCode);

            return R.data(true);
        } catch (Exception e) {
            document.setStatus(RagDocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            document.setUpdatedAt(LocalDateTime.now());
            ragDocumentMapper.updateById(document);
            return R.fail("重新分块失败: " + e.getMessage());
        }
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
