USE aftersale_order;

-- ---------------------------------------------------------------------------
-- 模拟订单库（独立库，非独立订单微服务）
-- aftersale-service 双数据源读取；演示订单挂在用户 id=5、商家 id=2
-- 订单状态 status：1待发货 2已发货 3已完成 4已关闭
-- 支付状态 pay_status：0未支付 1已支付
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS order_info;

CREATE TABLE order_info (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(64)    NOT NULL COMMENT '订单号',
    user_id     BIGINT         NOT NULL COMMENT '下单用户ID',
    merchant_id BIGINT         NOT NULL DEFAULT 2 COMMENT '商家用户ID（演示用 merchant 账号）',
    sku_id      BIGINT         NOT NULL COMMENT 'SKU ID',
    sku_name    VARCHAR(128)   NOT NULL COMMENT '商品名称快照',
    num         INT            NOT NULL COMMENT '购买数量',
    amount      DECIMAL(12, 2) NOT NULL COMMENT '订单金额',
    status      TINYINT        NOT NULL COMMENT '1待发货 2已发货 3已完成 4已关闭',
    pay_status  TINYINT        NOT NULL DEFAULT 0 COMMENT '0未支付 1已支付',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_sku_id (sku_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表（模拟，独立库）';

INSERT INTO order_info (order_no, user_id, merchant_id, sku_id, sku_name, num, amount, status, pay_status) VALUES
('ORD20250920001', 5, 2, 1001, '无线蓝牙耳机', 1, 199.00, 3, 1),
('ORD20250920002', 5, 2, 1002, '机械键盘',     1, 399.00, 2, 1),
('ORD20250920003', 5, 2, 1003, 'USB-C 扩展坞', 1, 129.00, 1, 1);
