# aftersale-backend

基于Spring Cloud开发的电商售后退换货平台，包括 **用户中心（portal）** 、 **网关认证（gateway）** 以及 **库存**、**售后**两大服务。

## 功能（当前）

- **portal**：用户 / 部门 CRUD、多角色、权限校验、登录校验（不签发 Token）、写操作 Redis 锁、**站内通知**
- **gateway**：登录签发 JWT、校验 Token、续期、登出黑名单、白名单、把用户信息注入 `X-User-*` 后转发下游
- **stock-service**：SKU 档案 + 库存扣减/回退
- **aftersale-service**：模拟订单、申请/列表/详情、审核/驳回/撤回（状态机，无 Activiti）
- **统一返回**：`{code, msg, data}`，错误码见 `ErrorEnum`

## 模块

```
aftersale-backend
├── common/                 # 公共 jar：R、BizException、ErrorEnum、角色权限枚举
├── portal/                 # 用户中心，端口 8081，库 aftersale_user（含 notify）
├── gateway/                # 网关，端口 8080，不连业务库
├── basic/
│   ├── stock-service/      # 库存，端口 8083，库 aftersale_stock
│   └── aftersale-service/  # 售后，端口 8082，库 aftersale_core
├── config-file/
│   ├── sql/                # 建库建表脚本
│   └── rocketmq/           # Broker 配置
└── docker-compose.yml      # 可选：RocketMQ、Nacos
```

| 服务 | 端口 | 职责 |
|------|------|------|
| gateway | 8080 | 登录鉴权、路由 |
| portal | 8081 | 用户 / 部门 / 通知 |
| aftersale-service | 8082 | 售后 |
| stock-service | 8083 | SKU 档案 + 库存变动 |

## 代码层级（当前）

```
aftersale-backend
├── common/src/main/java/com/yean/common/
│   ├── result/R                 # 统一 {code,msg,data}
│   ├── exception/               # BizException、MVC GlobalExceptionHandler
│   ├── enums/                   # ErrorEnum、RoleEnum、PermissionEnum
│   └── web/EmptyStringToNullAdvice
│
├── portal/ …/com.yean.portal/
│   ├── user/                    # 用户 CRUD、authenticate、角色授权
│   ├── dept/                    # 部门树
│   ├── notify/                  # 站内通知
│   └── config/                  # Redis、事务、写操作锁
│
├── gateway/ …/com.yean.gateway/
│   ├── handler/                 # 登录 / 登出 / 续期 RouterFunction
│   ├── auth/                    # 调 portal 校验账号
│   ├── jwt/                     # 签发、解析、Redis 黑名单
│   ├── filter/AuthGlobalFilter  # 白名单 + 注入 X-User-*
│   └── exception/               # WebFlux 统一 JSON 错误
│
├── basic/stock-service/ …/com.yean.stock/
│   ├── sku/                     # SKU 档案：CRUD、预警阈值、update_by、缓存
│   ├── stock/                   # 库存变动：扣减/回退/流水、operator
│   └── config/                  # Redis、Redisson 锁、操作人解析
│
└── basic/aftersale-service/ …/com.yean.aftersale/
    ├── order/                   # 模拟订单查询
    ├── aftersale/               # 售后单：申请/审核/驳回/撤回、操作日志
    ├── message/                 # 本地消息表 outbox（先落库，暂不发 MQ）
    └── config/                  # Redis、Redisson 锁、操作人解析
```

接口前缀（均经 gateway `/api/**` 转发，也可本服务端口直连）：

| 模块 | 路径 |
|------|------|
| 登录登出 | `/api/auth/**`（只在 gateway） |
| 用户 / 部门 / 通知 | `/api/user/**` `/api/dept/**` `/api/notify/**` → portal |
| SKU 档案 | `/api/stock/sku/**` → stock-service |
| 库存变动 | `/api/stock/deduct` `rollback` `{skuId}` `flow/list` → stock-service |
| 模拟订单 | `/api/aftersale/order/**` → aftersale-service |
| 售后单 | `/api/aftersale/apply` `page` `{aftersaleNo}` `approve` `reject` `withdraw` |


## 环境（根据自己的端口、账号密码调整配置）

- JDK 17
- Maven 3.x（可用 IDEA 自带）
- MySQL 8
- Redis 7

## 使用方法

### 1. MySQL

本机已安装时，启动后导入脚本（密码按你的实际修改）：

```bash
mysql -uroot -p < config-file/sql/01-init.sql
mysql -uroot -p < config-file/sql/02-user.sql
mysql -uroot -p < config-file/sql/03-stock.sql
mysql -uroot -p < config-file/sql/04-aftersale.sql
```

`01` 建三个库；`02` 用户中心；`03` 库存 SKU；`04` 模拟订单和售后表。演示订单挂在用户 id=5（精神状态良好），商家 id=2（AAA代购）。

### 2. Redis

portal 写用户/部门要加锁，gateway 登出黑名单要用 Redis，库存扣减、售后单号/幂等/审核锁也走 Redis。

```bash
redis-server
```

另开终端检查：

```bash
redis-cli ping    # 应返回 PONG
```

关掉：`Ctrl+C`，或 `redis-cli shutdown`。

### 3. IDEA 运行

1. 用 IntelliJ 打开本仓库根目录，选 **JDK 17**，Maven 重新加载。
2. 先启动 **portal**：运行 `com.yean.portal.PortalApplication`。
3. 再启动 **gateway**：运行 `com.yean.gateway.GatewayApplication`。
4. 库存：**stock-service** 运行 `com.yean.stock.StockApplication`（端口 8083）。
5. 售后：**aftersale-service** 运行 `com.yean.aftersale.AftersaleApplication`（端口 8082）。

默认 profile 是 `local`（不注册 Nacos）。要注册 Nacos 时，运行配置加上：

```
--spring.profiles.active=nacos
```

并保证 Nacos 已启动。

Swagger：

- 网关：http://127.0.0.1:8080/swagger-ui.html（登录/登出是 RouterFunction，以 curl 为准）
- 用户中心：http://127.0.0.1:8081/swagger-ui.html
- 库存：http://127.0.0.1:8083/swagger-ui.html
- 售后：http://127.0.0.1:8082/swagger-ui.html

经网关访问业务接口时，Header 加 `Authorization: Bearer <token>`。

命令行示例：

```bash
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"00000","password":"123456"}'

curl http://127.0.0.1:8080/api/user/list \
  -H 'Authorization: Bearer <上一步返回的 token>'

curl http://127.0.0.1:8080/api/stock/1001 \
  -H 'Authorization: Bearer <token>'

curl -X POST http://127.0.0.1:8080/api/stock/deduct \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"skuId":1001,"changeNum":1,"bizNo":"AS-DEMO-001"}'

# 本地直连售后（用户 id=5 申请 ORD20250920001）
curl -X POST http://127.0.0.1:8082/api/aftersale/apply \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORD20250920001","skuId":1001,"type":1,"num":1,"reason":"质量问题","userId":5,"username":"精神状态良好","role":"USER"}'
```

### 4. Docker（可选）

当前 `docker-compose.yml` 只起 **RocketMQ、Nacos**，给后面的库存/售后用。本地开发 portal + gateway **可以不启 Docker**。

需要较新的 Docker Desktop（macOS 13 装不了最新版，可用 4.48.0 或本机 Redis/MySQL 替代）。

```bash
docker compose up -d
```

- Nacos：http://127.0.0.1:8848/nacos （`nacos` / `nacos`）
- RocketMQ NameServer：`9876`

停掉：

```bash
docker compose down
```

