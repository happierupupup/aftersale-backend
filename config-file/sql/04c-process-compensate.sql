USE aftersale;

-- 流程补偿任务：本地事务失败且 process 已推进时入队，扫表重试 rollback，直到对齐 expected_task_key
CREATE TABLE IF NOT EXISTS process_compensate (
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
