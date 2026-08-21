ALTER TABLE `agent_definition`
    ADD COLUMN `rag_config` text DEFAULT NULL COMMENT 'RAG检索参数配置（topK/scoreThreshold/ragMode）' AFTER `model_params_override`;
