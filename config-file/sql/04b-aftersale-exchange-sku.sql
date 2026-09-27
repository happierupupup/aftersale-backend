-- 已有库增量：售后单增加换货目标 SKU（阶段 7′）
USE aftersale_core;

ALTER TABLE aftersale_ticket
    ADD COLUMN exchange_sku_id BIGINT DEFAULT NULL COMMENT '换货目标 SKU；仅 type=2 有值'
        AFTER sku_name;
