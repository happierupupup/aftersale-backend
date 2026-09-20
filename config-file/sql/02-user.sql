USE aftersale_user;

-- ---------------------------------------------------------------------------
-- portal 用户中心库
-- 角色见 RoleEnum：USER / MERCHANT / ADMIN / WAREHOUSE / FINANCE
-- 一人可多角色，关系表 sys_user_role；sys_user.role 存主角色（列表第一项）
-- 演示账号密码均为 123456（BCrypt）
-- ---------------------------------------------------------------------------

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

-- 部门树
-- 总公司
--   ├── 运营中心
--   │     ├── 商家组
--   │     └── 客服组
--   ├── 仓储中心
--   │     └── 质检组
--   └── 财务中心
INSERT INTO sys_dept (id, name, parent_id, level) VALUES
(1, '总公司',   0, 1),
(2, '运营中心', 1, 2),
(3, '商家组',   2, 3),
(4, '客服组',   2, 3),
(5, '仓储中心', 1, 2),
(6, '质检组',   5, 3),
(7, '财务中心', 1, 2);

-- 密码 123456
INSERT INTO sys_user (id, username, password, role, dept_id) VALUES
(1, '00000',     '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'ADMIN',     1),
(2, 'AAA代购',  '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'MERCHANT',  3),
(3, 'AAA极速发货', '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'WAREHOUSE', 5),
(4, '打工人',   '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'FINANCE',   7),
(5, '精神状态良好',      '$2y$10$HPWRYMOoURtYco13RZCb0OOic51AlX2JzOEs/ZKOlskh1cb8PKL2G', 'USER',      4);

INSERT INTO sys_user_role (user_id, role) VALUES
(1, 'ADMIN'),
(2, 'MERCHANT'),
(3, 'WAREHOUSE'),
(4, 'FINANCE'),
(5, 'USER');

INSERT INTO sys_notify (user_id, title, content, channel, biz_type, read_flag) VALUES
(5, '欢迎使用售后平台', '售后进度将以站内信通知，请留意本列表。', 'INBOX', 'SYSTEM', 0),
(5, '演示：审核通过', '您的售后单 AS202609200001 商家已同意，请按指引寄回。', 'INBOX', 'AFTERSALE', 0);
