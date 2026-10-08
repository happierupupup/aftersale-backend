USE aftersale;

-- ---------------------------------------------------------------------------
-- portal 用户中心表（sys_*）
-- 角色见 RoleEnum：USER / MERCHANT / ADMIN / WAREHOUSE
-- 一人可多角色，关系表 sys_user_role；sys_user.role 存主角色（列表第一项）
-- seed accounts password: 123456 (BCrypt); usernames ASCII only
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS sys_operate_log;
DROP TABLE IF EXISTS sys_notify;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;

CREATE TABLE sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '部门名称',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，0表示根',
    level       TINYINT      NOT NULL DEFAULT 1 COMMENT '层级，从1开始',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表（邻接表）';

CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密密码',
    role        VARCHAR(32)  NOT NULL COMMENT '主角色，与 sys_user_role 第一项保持一致',
    dept_id     BIGINT       NOT NULL COMMENT '部门ID',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_dept_id (dept_id),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE sys_user_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    role        VARCHAR(32) NOT NULL COMMENT '角色编码，对应 RoleEnum',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系';

CREATE TABLE sys_notify (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT        NOT NULL COMMENT '接收用户 ID',
    title       VARCHAR(128)  NOT NULL COMMENT '标题',
    content     VARCHAR(1024) NOT NULL COMMENT '正文',
    channel     VARCHAR(16)   NOT NULL DEFAULT 'INBOX' COMMENT 'INBOX 站内信 / SMS 短信 / MAIL 邮件',
    biz_type    VARCHAR(32)   NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM / AFTERSALE / STOCK',
    read_flag   TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_user_read (user_id, read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知（站内信为主，短信/邮件仅记录）';

CREATE TABLE sys_operate_log (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    module        VARCHAR(64)   NOT NULL COMMENT '业务模块，如 user/stock/aftersale',
    action        VARCHAR(64)   NOT NULL COMMENT '动作，如 create/update/approve',
    method        VARCHAR(16)            DEFAULT NULL COMMENT 'HTTP 方法',
    path          VARCHAR(256)           DEFAULT NULL COMMENT '请求路径',
    operator_id   BIGINT                 DEFAULT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64)            DEFAULT NULL COMMENT '操作人名称',
    operator_role VARCHAR(32)            DEFAULT NULL COMMENT '操作人角色',
    success       TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    cost_ms       INT                    DEFAULT NULL COMMENT '耗时毫秒',
    remark        VARCHAR(512)           DEFAULT NULL COMMENT '备注/异常摘要',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_module_action (module, action),
    KEY idx_operator_id (operator_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口操作日志';

-- dept tree: HQ -> Ops/{Merchant_Team,CS_Team}, Warehouse_Center/QC_Team
INSERT INTO sys_dept (id, name, parent_id, level) VALUES
(1, 'HQ',               0, 1),
(2, 'Ops',              1, 2),
(3, 'Merchant_Team',    2, 3),
(4, 'CS_Team',          2, 3),
(5, 'Warehouse_Center', 1, 2),
(6, 'QC_Team',          5, 3);

-- password for all seed users: 123456 (BCrypt)
INSERT INTO sys_user (id, username, password, role, dept_id) VALUES
(1, 'admin',     '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'ADMIN',     1),
(2, 'merchant',  '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'MERCHANT',  3),
(3, 'warehouse', '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'WAREHOUSE', 5),
(5, 'user',      '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'USER',      4);

INSERT INTO sys_user_role (user_id, role) VALUES
(1, 'ADMIN'),
(2, 'MERCHANT'),
(3, 'WAREHOUSE'),
(5, 'USER');

INSERT INTO sys_notify (user_id, title, content, channel, biz_type, read_flag) VALUES
(5, 'Welcome', 'Aftersale updates will appear in this inbox.', 'INBOX', 'SYSTEM', 0),
(5, 'Sample_Approved', 'Ticket AS202609200001 was approved. Please ship the item back.', 'INBOX', 'AFTERSALE', 0);

