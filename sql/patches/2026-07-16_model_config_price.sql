-- model_config 加输入/输出单价列：成本模块的价格地基。
--
-- 背景：成本中心按「元 / 百万 token」对每次对话 run 计算成本快照落流水
-- （chat_usage_record），价格配在模型上、由管理员按供应商官网报价填写，
-- 币种统一人民币。NULL=未配价：该模型的用量照记 token 但不计成本，
-- 统计页会提示补配；本地模型（如 Ollama）填 0。改价只影响之后的新流水，
-- 历史修正走「重算历史成本」工具。
-- 关联改动：ModelConfig/ModelConfigVO 加 inputPrice/outputPrice、
-- 前端 ModelConfigForm 配价输入、sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `model_config`
  ADD COLUMN `input_price` decimal(12,4) DEFAULT NULL COMMENT '输入单价（元/百万token；NULL=未配价，0=免费/本地）' AFTER `last_connectivity_check`,
  ADD COLUMN `output_price` decimal(12,4) DEFAULT NULL COMMENT '输出单价（元/百万token；NULL=未配价，0=免费/本地）' AFTER `input_price`;
