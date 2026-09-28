USE aftersale;

-- ---------------------------------------------------------------------------
-- SKU / 库存表（stock_*，trade-service）
-- SKU 档案：stock_sku（低频改：名称、预警阈值、update_by）
-- 库存计数：stock_count（高频写：stock_num + version 乐观锁）
-- 库存流水：stock_flow（operator；幂等 uk_biz_no_type）
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS stock_flow;
DROP TABLE IF EXISTS stock_count;
DROP TABLE IF EXISTS stock_sku;

CREATE TABLE stock_sku (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id         BIGINT       NOT NULL COMMENT 'SKU 业务标识（同一商品不同规格各一个）',
    sku_name       VARCHAR(128) NOT NULL COMMENT '规格名称',
    merchant_id    BIGINT       NOT NULL DEFAULT 2 COMMENT '归属商家用户ID（sys_user.id，售后通知推送对象）',
    warn_threshold INT          NOT NULL DEFAULT 10 COMMENT '库存预警阈值',
    update_by      VARCHAR(64)           DEFAULT NULL COMMENT '档案最后维护人',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_id (sku_id),
    KEY idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU档案';

CREATE TABLE stock_count (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    sku_id      BIGINT   NOT NULL COMMENT 'SKU ID，与 stock_sku.sku_id 对应',
    stock_num   INT      NOT NULL DEFAULT 0 COMMENT '可用库存',
    version     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_id (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU可用库存（热点计数）';

CREATE TABLE stock_flow (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    flow_no     VARCHAR(64)  NOT NULL COMMENT '流水号',
    sku_id      BIGINT       NOT NULL COMMENT 'SKU ID',
    change_num  INT          NOT NULL COMMENT '变更数量（正数）',
    type        VARCHAR(16)  NOT NULL COMMENT 'DEDUCT扣减 ROLLBACK回退',
    biz_no      VARCHAR(64)  NOT NULL COMMENT '关联业务单号（如售后单号）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
    operator    VARCHAR(64)           DEFAULT NULL COMMENT '扣减/回退操作人',
    remark      VARCHAR(256)          DEFAULT NULL COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_no (flow_no),
    UNIQUE KEY uk_biz_no_type (biz_no, type),
    KEY idx_sku_id (sku_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水';

INSERT INTO stock_sku (sku_id, sku_name, merchant_id, warn_threshold) VALUES
(1001, '无线蓝牙耳机', 2, 20),
(1002, '机械键盘',     2, 10),
(1003, 'USB-C 扩展坞',  2,  5);

INSERT INTO stock_count (sku_id, stock_num, version) VALUES
(1001, 500, 0),
(1002, 120, 0),
(1003,  80, 0);
