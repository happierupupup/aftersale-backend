-- 已有库增量：售后单增加换货目标 SKU（阶段 7′）
-- 若已按 04-aftersale.sql 全量建表（含 exchange_sku_id），可跳过本脚本
USE aftersale;

ALTER TABLE aftersale_ticket
    ADD COLUMN exchange_sku_id BIGINT DEFAULT NULL COMMENT '换货目标 SKU；仅 type=2 有值'
        AFTER sku_name;
