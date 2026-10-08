USE aftersale;

-- ---------------------------------------------------------------------------
-- 售后表（aftersale_*，trade-service / ticket 域；订单见 05-order.sql）
-- 售后单表：aftersale_ticket（Java 域对象 Ticket）
-- 售后类型 type：1退货 2换货 3仅退款
-- 售后状态 status：-1草稿 0待审核 1待仓库发货 2待用户收货 3待退款 4已办结 5已驳回 6已撤回
-- 草稿：用户「保存」写入，active_flag=NULL（不占 uk_order_sku 进行中位），不启流程；「提交」先删草稿再新增正式单
-- 主链路：申请→待审核→(通过)待仓库发货→待用户收货→待退款→已办结；仅退款审核后直达待退款
-- 站内信走 sys_notify，此处不建 notification
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS local_message;
DROP TABLE IF EXISTS aftersale_oper_log;
DROP TABLE IF EXISTS aftersale_ticket;
DROP TABLE IF EXISTS aftersale_order; -- 兼容旧表名

CREATE TABLE aftersale_ticket (
    id                   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    aftersale_no         VARCHAR(32)    NOT NULL COMMENT '售后单号',
    order_no             VARCHAR(64)    NOT NULL COMMENT '关联订单号',
    user_id              BIGINT         NOT NULL COMMENT '申请用户ID',
    merchant_id          BIGINT         NOT NULL COMMENT '商家ID',
    type                 TINYINT        NOT NULL COMMENT '1退货 2换货 3仅退款',
    status               TINYINT        NOT NULL DEFAULT 0 COMMENT '-1草稿 0待审核 1待仓库发货 2待用户收货 3待退款 4已办结 5已驳回 6已撤回',
    sku_id               BIGINT         NOT NULL COMMENT 'SKU ID（原订单商品）',
    sku_name             VARCHAR(128)   NOT NULL COMMENT '商品名称快照',
    exchange_sku_id      BIGINT                  DEFAULT NULL COMMENT '换货目标 SKU；仅 type=2 有值',
    num                  INT            NOT NULL COMMENT '售后数量',
    amount               DECIMAL(12, 2) NOT NULL COMMENT '退款/换货涉及金额（部分售后按数量比例折算）',
    reason               VARCHAR(512)            DEFAULT NULL COMMENT '申请原因',
    logistics_info       JSON                    DEFAULT NULL COMMENT '物流信息',
    refund_detail        JSON                    DEFAULT NULL COMMENT '退款明细',
    -- 进行中=1；驳回/撤回置 NULL，释放 (order_no, sku_id) 申请位。MySQL 唯一索引允许多个 NULL
    active_flag          TINYINT                 DEFAULT 1 COMMENT '进行中=1；草稿/驳回/撤回=NULL（不占 uk_order_sku）',
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
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_id (biz_id),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息表（outbox）';

DROP TABLE IF EXISTS process_compensate;
CREATE TABLE process_compensate (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    aftersale_no       VARCHAR(32)  NOT NULL COMMENT '售后单号 / process businessKey',
    expected_task_key  VARCHAR(64)  NOT NULL COMMENT '本地未改成功前应对齐的流程节点，如 warehouseShip',
    cause              VARCHAR(512)          DEFAULT NULL COMMENT '触发原因（本地异常摘要）',
    status             VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW/DONE/FAIL',
    retry_count        INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    last_error         VARCHAR(512)          DEFAULT NULL COMMENT '最近一次失败信息',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status_retry (status, next_retry_time),
    KEY idx_aftersale_no (aftersale_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程补偿扫表（本地失败后 rollback 重试）';
