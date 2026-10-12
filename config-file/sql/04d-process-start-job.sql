USE aftersale;

-- 流程启动任务：apply 先落售后单，扫表异步 process/start，成功后再写 processInstanceId + 通知
CREATE TABLE IF NOT EXISTS process_start_job (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    aftersale_no       VARCHAR(32)  NOT NULL COMMENT '售后单号 / process businessKey',
    process_def_key    VARCHAR(64)  NOT NULL COMMENT 'Activiti 流程定义 key',
    applicant_user_id  BIGINT       NOT NULL COMMENT '申请人用户 ID',
    operator_name      VARCHAR(64)           DEFAULT NULL COMMENT '申请操作人',
    operator_role      VARCHAR(32)           DEFAULT NULL COMMENT '申请角色',
    remark             VARCHAR(512)          DEFAULT NULL COMMENT '申请备注',
    status             VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW/DONE/FAIL/CANCELLED',
    retry_count        INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    last_error         VARCHAR(512)          DEFAULT NULL COMMENT '最近一次失败信息',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_aftersale_no (aftersale_no),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程启动扫表（先落单后异步 start）';
