USE aftersale_core;

-- ---------------------------------------------------------------------------
-- aftersale-service 售后核心库
-- 售后类型 type：1退货 2换货 3仅退款
-- 售后状态 status：0待审核 1审核通过 2待寄回 3待验货 4待退款 5已办结 6已驳回 7已撤回
-- 订单状态 order_info.status：1待发货 2已发货 3已完成 4已关闭
-- 支付状态 order_info.pay_status：0未支付 1已支付
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS local_message;
DROP TABLE IF EXISTS aftersale_oper_log;
DROP TABLE IF EXISTS aftersale_order;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表（模拟）';

CREATE TABLE aftersale_order (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    aftersale_no         VARCHAR(32)    NOT NULL COMMENT '售后单号',
    order_no             VARCHAR(64)    NOT NULL COMMENT '关联订单号',
    user_id              BIGINT         NOT NULL COMMENT '申请用户ID',
    merchant_id          BIGINT         NOT NULL COMMENT '商家ID',
    type                 TINYINT        NOT NULL COMMENT '1退货 2换货 3仅退款',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '0待审核 1审核通过 2待寄回 3待验货 4待退款 5已办结 6已驳回 7已撤回',
    sku_id               BIGINT         NOT NULL COMMENT 'SKU ID',
    sku_name             VARCHAR(128)   NOT NULL COMMENT '商品名称快照',
    num                  INT            NOT NULL COMMENT '售后数量',
    amount               DECIMAL(12, 2) NOT NULL COMMENT '退款/换货涉及金额（部分售后按数量比例折算）',
    reason               VARCHAR(512)            DEFAULT NULL COMMENT '申请原因',
    logistics_info       JSON                    DEFAULT NULL COMMENT '物流信息',
    refund_detail        JSON                    DEFAULT NULL COMMENT '退款明细',
    -- 进行中=1；驳回/撤回置 NULL，释放 (order_no, sku_id) 申请位。MySQL 唯一索引允许多个 NULL
    active_flag          TINYINT                 DEFAULT 1 COMMENT '进行中=1，终态（驳回/撤回）=NULL',
    process_instance_id  VARCHAR(64)             DEFAULT NULL COMMENT 'Activiti流程实例ID',
    create_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_aftersale_no (aftersale_no),
    UNIQUE KEY uk_order_sku (order_no, sku_id, active_flag),
    KEY idx_user_id (user_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_status (status),
    KEY idx_type (type),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后单表';

CREATE TABLE aftersale_oper_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    aftersale_no  VARCHAR(32)  NOT NULL COMMENT '售后单号',
    operator_id   BIGINT                DEFAULT NULL COMMENT '操作人ID，系统任务为NULL',
    operator_name VARCHAR(64)           DEFAULT NULL COMMENT '操作人名称',
    action        VARCHAR(32)  NOT NULL COMMENT '操作类型：APPLY/APPROVE/REJECT/WITHDRAW/SYSTEM',
    before_status TINYINT               DEFAULT NULL COMMENT '变更前状态',
    after_status  TINYINT               DEFAULT NULL COMMENT '变更后状态',
    remark        VARCHAR(512)          DEFAULT NULL COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_aftersale_no (aftersale_no),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后操作日志';

CREATE TABLE local_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_id          VARCHAR(64)  NOT NULL COMMENT '业务唯一键，如售后单号+事件类型',
    topic           VARCHAR(64)  NOT NULL COMMENT 'RocketMQ Topic',
    payload         TEXT         NOT NULL COMMENT '消息体JSON',
    status          VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW/SENT/FAIL',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_id (biz_id),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息表（outbox）';

CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID',
    title       VARCHAR(128) NOT NULL COMMENT '标题',
    content     VARCHAR(512) NOT NULL COMMENT '内容',
    biz_id      VARCHAR(64)           DEFAULT NULL COMMENT '关联业务ID，用于幂等',
    read_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_biz (user_id, biz_id),
    KEY idx_user_read (user_id, read_flag),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信通知';

-- 演示订单（user 账号 id=5，merchant id=2）
INSERT INTO order_info (order_no, user_id, merchant_id, sku_id, sku_name, num, amount, status, pay_status) VALUES
('ORD20250920001', 5, 2, 1001, '无线蓝牙耳机', 1, 199.00, 3, 1),
('ORD20250920002', 5, 2, 1002, '机械键盘',     1, 399.00, 2, 1),
('ORD20250920003', 5, 2, 1003, 'USB-C 扩展坞', 1, 129.00, 1, 1);
