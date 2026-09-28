-- 流程投影表（process_*，process-service / Activiti）
-- act_* 表由 Activiti databaseSchemaUpdate=true 自动创建
USE aftersale;

DROP TABLE IF EXISTS process_oper_log;
DROP TABLE IF EXISTS process_biz;

CREATE TABLE process_biz (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    business_key          VARCHAR(64)  NOT NULL COMMENT '业务键=售后单号',
    process_instance_id   VARCHAR(64)           DEFAULT NULL COMMENT 'Activiti 实例 ID',
    process_def_key       VARCHAR(64)  NOT NULL COMMENT 'return/exchange/refund',
    biz_status            VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/REJECTED/CANCELED',
    current_task_key      VARCHAR(64)           DEFAULT NULL COMMENT '当前 userTask id',
    applicant_user_id     BIGINT       NOT NULL COMMENT '申请人用户 ID',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_key (business_key),
    KEY idx_instance (process_instance_id),
    KEY idx_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程业务投影';

CREATE TABLE process_oper_log (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    business_key          VARCHAR(64)  NOT NULL COMMENT '业务键',
    process_instance_id   VARCHAR(64)           DEFAULT NULL,
    action                VARCHAR(32)  NOT NULL COMMENT 'START/COMPLETE/REJECT/WITHDRAW/ROLLBACK',
    task_key              VARCHAR(64)           DEFAULT NULL COMMENT '操作涉及的任务',
    from_task_key         VARCHAR(64)           DEFAULT NULL,
    to_task_key           VARCHAR(64)           DEFAULT NULL,
    operator              VARCHAR(64)           DEFAULT NULL,
    role                  VARCHAR(32)           DEFAULT NULL,
    remark                VARCHAR(512)          DEFAULT NULL,
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz_key (business_key),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程操作日志';
