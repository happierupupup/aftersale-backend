USE aftersale;

-- SKU 归属商家：申请售后按商品查 merchant_id，通知推送给该用户
ALTER TABLE stock_sku
    ADD COLUMN merchant_id BIGINT NOT NULL DEFAULT 2 COMMENT '归属商家用户ID（sys_user.id）' AFTER sku_name,
    ADD KEY idx_merchant_id (merchant_id);

UPDATE stock_sku SET merchant_id = 2 WHERE merchant_id IS NULL OR merchant_id = 0;
