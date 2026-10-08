# aftersale-backend

Spring Cloud 电商**售后退换货**后端：网关鉴权 + 用户中心 + 交易业务（订单 / 售后 / SKU 库存）+ Activiti 流程引擎。

技术栈：Spring Boot 2.7、Spring Cloud Gateway、OpenFeign、MyBatis-Plus、Redisson、Activiti 6、MySQL 8、Redis、RocketMQ 4.9（通知）。

配套前端：[happierupupup/aftersale-frontend](https://github.com/happierupupup/aftersale-frontend)— Vue3 + TS + Vite5 + Element Plus，开发时代理到 gateway `:8080`。

## 模块与端口

```
IdeaProjects/
├── aftersale-backend/          # 本仓库
│   ├── common/                 # 公共 jar：R、异常、角色权限、RedisBizLock、@OperateLog、Feign
│   ├── portal/                 # 用户中心 :8081（sys_* 表）
│   ├── gateway/                # 网关鉴权 :8080（不连业务库）
│   ├── basic/
│   │   ├── trade-service/      # 交易 :8082（订单 + 售后 + SKU/库存；Feign 流程；通知）
│   │   └── process-service/    # Activiti :8083
│   ├── config-file/
│   │   ├── sql/                # 建库建表（单库 aftersale）
│   │   └── rocketmq/           # 本机启停脚本
│   ├── docs/                   # 本地手册（默认 gitignore）：plan / test / problem 等
│   └── docker-compose.yml
└── aftersale-frontend/         # 前端仓库 clone 至此（联调 :5173）
```

| 服务 | 端口 | 职责 |
|------|------|------|
| gateway | 8080 | JWT 登录/续期/登出、路由、注入 `X-User-*` |
| portal | 8081 | 用户 / 部门 / 通知 / 操作日志落库 |
| trade-service | 8082 | 订单、售后单、SKU 档案、库存扣减回退、售后 MQ 通知 |
| process-service | 8083 | Activiti：start/complete/reject/withdraw/rollback/history |
| Redis | 6379 | 锁 / 缓存 / JWT 黑名单 / 幂等键（必启） |
| RocketMQ NameServer | 9876 | 售后通知 Topic（测站内信时需要） |

`trade-service` 包结构（`com.yean.trade`）：

| 包 | 含义 | 表 |
|----|------|-----|
| `order` | 模拟订单 | `order_info` |
| `ticket` | 售后单 + `strategy`/`rpc`（Feign 流程） | `aftersale_ticket` 等 |
| `sku` | SKU 档案 | `stock_sku` |
| `inventory` | 库存计数 / 流水 | `stock_count` / `stock_flow` |
| `notify` | 售后通知：outbox → MQ → Consumer → portal（阶段 8b） | — |
| `message` | 本地消息 outbox + 扫表投递 | `local_message` |

`common`：`dto` / `rpc` / `config` + `enums` / `exception` / `result` / `lock`。  
全项目共用 MySQL 库 **`aftersale`**，用表名区分域；portal / trade / process 均连同一库。

## 售后流程（已可推进）

申请页两个按钮：

| 前端按钮 | 接口 | 行为 |
|----------|------|------|
| **保存** | `POST /aftersale/draft` | 同用户+订单+SKU **upsert** 草稿；`status=-1`，`active_flag=NULL`；**不启流程**；商家列表不可见 |
| **提交** | `POST /aftersale/apply` | **先删同单草稿**，再新增正式单 + Feign `process/start`，停在商家审核 |

进申请页回填：`GET /aftersale/draft?orderNo=&skuId=`（或 `/draft/mine`），看返回里的 **`draft: true`** / `status: -1`。

正式单推进：商家查待审核列表（或详情）后：

- **`approve`**：按类型走不同 BPMN 通道  
- **`reject`**（可带 `remark`）：流程结束 → **已驳回**（释放 `active_flag`，可重新申请）  
- **`withdraw`**：待审核或待仓库发货（发货前）→ **已撤回**

| type | 含义 | 审核通过后节点 |
|------|------|----------------|
| 1 | 退货 | 仓库发货 → 用户收货（旧品回库）→ 商家退款 → 办结 |
| 2 | 换货（须 `exchangeSkuId`） | 仓库发货（扣新 SKU）→ 用户收货（旧品回库）→ 办结 |
| 3 | 仅退款 | 商家退款 → 办结（**不经仓库**） |

角色：审核/退款=`MERCHANT`（且商家归属）、发货=`WAREHOUSE`、收货=申请人 `USER`。`DONE`/`REJECTED`/`CANCELED` 后再改会被拒绝（终态只读）。列表/详情按角色强制过滤（USER 本人、MERCHANT 本商家）。

售后状态码：

```text
-1 草稿 →（提交）0 待审核 → 1 待仓库发货 → 2 待用户收货 → 3 待退款 → 4 已办结
                              ↘ 5 已驳回 / 6 已撤回
```

`/aftersale/page` **默认排除草稿**（`status >= 0`）；显式 `status=-1` 可查草稿。

通知（阶段 **8b**）：节点流转与 `local_message(NEW)` **同事务** → Sender 投递 Topic `aftersale_notify` → Consumer → portal `/notify/send`。申请时 **`merchant_id` 取自 `stock_sku`**。测通知见本地 `docs/test.md` §7。

催办：每天 **10:00** 扫待审核/待发货/待退款；手动 `POST /aftersale/job/pending-remind`。  
超时关单：每天 **2:30** 关闭创建超过 48h 仍停在上述节点的单；手动 `POST /aftersale/job/timeout`。

## 能力概览

- **portal**：用户部门 CRUD、多角色、登录校验（不签发 Token）、站内通知、操作日志落库
- **gateway**：双 JWT（短 Access + HttpOnly Refresh Cookie；续期轮换拉黑旧 Refresh）、Redis 黑名单登出、白名单、**路径级角色 ACL**、**Sentinel 限流**、注入 `X-User-*`
- **trade / order**：列表、详情、单笔新增、CSV 批量上传
- **trade / ticket**：草稿保存/加载/删除；`apply` 提交；Feign 驱动 Activiti；策略同步状态/库存；数据权限 + 终态只读
- **process-service**：BPMN、候选组、终态守卫、`process_oper_log`、回退上一节点
- **trade / sku + inventory**：SKU CRUD；扣减/回退（幂等 + 锁 + CAS）；写接口限 WAREHOUSE/ADMIN
- **trade / notify + message**：售后事件 → outbox → RocketMQ → 站内信（8b）
- **统一返回**：`{code, msg, data}`，分页 `PageResult`，错误码 `ErrorEnum`
- **前端**：登录按角色菜单；售后工作台左列表右详情；管理员用户管理；申请页保存/提交

## 关键设计

- **双 JWT + Redis 黑名单**：短 Access（Bearer）+ 长 Refresh（HttpOnly Cookie）；续期轮换拉黑旧 `jti`；下游只认 `X-User-*`，多网关共享登录态。
- **流程独立 + 节点策略**：Activiti 仅 `process-service`；trade Feign 推节点后走 `TicketNodeStrategy`（发货扣新 SKU、收货回旧品等），BPMN 定路径、策略定副作用。
- **草稿与进行中槽位**：同表草稿 `status=-1`、`active_flag=NULL`、不启流程；**提交先删草稿再新增正式单**；进行中 `(order_no, sku_id, active_flag)` 唯一，驳回/撤回释槽可再申请。
- **跨服务最终一致**：主状态同步（流程 RPC + 本地事务）；站内信走 outbox（`local_message`）→ RocketMQ → 幂等消费；流程已推进而本地失败时写 `process_compensate` 扫表重试 rollback（防长期半成功）。
- **库存三件套**：Redisson 锁串行入口 + DB `version` CAS 防超卖 + `stock_flow` 唯一键幂等；SKU Cache Aside，写后**延迟双删**。
- **多实例安全**：定时催办/超时关单/outbox 扫表用 Redisson `tryLock`；申请与审核操作 Redis SET NX 防重。
- **定时任务**：outbox 每 5s 扫表投递；流程补偿每 10s 扫 `process_compensate`；催办每天 10:00；超时关单每天 2:30（创建超 48h 未处理则关单）。
- **旁路小线程池**：`@OperateLog` 固定池异步落库，不挡主链路；SKU 延迟双删用单线程 `ScheduledExecutor`（订单 CSV 批量仍同步，线程池对照未上）。
- **可观测**：业务维 `sys_operate_log` / `aftersale_oper_log` / `process_oper_log`；链路维 **`X-Trace-Id`**（gateway 生成或透传 → Feign/WebClient 下游；日志 MDC `%X{traceId}`）。

## 接口前缀（经 gateway，也可直连服务端口）

| 模块 | 路径 |
|------|------|
| 登录 / 续期 / 登出 | `/api/auth/login` · `/refresh` · `/logout`（仅 gateway；Refresh 走 Cookie） |
| 用户 / 部门 / 通知 / 操作日志 | `/user/**` `/dept/**` `/notify/**` `/operatelog/**` → portal |
| 订单 | `/order/**` → trade |
| 售后 | `/aftersale/**` → trade（含 draft / apply / rollback / job） |
| 本地消息 | `/message/job/**` → trade（扫表投递） |
| SKU | `/sku/**` → trade |
| 库存 | `/stock/**` → trade |
| 流程（调试） | `/process/**` → process-service（主路径为 trade Feign） |

### 售后草稿 / 提交（联调常用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/aftersale/draft` | 保存草稿（部分字段可空；同用户+订单+SKU upsert） |
| GET | `/aftersale/draft?orderNo=&skuId=` | 加载当前用户该单草稿；无则 `data=null` |
| GET | `/aftersale/draft/mine` | 当前用户全部草稿 |
| DELETE | `/aftersale/draft/{aftersaleNo}` | 删草稿 |
| POST | `/aftersale/apply` | **提交**：先删草稿再新增正式单 + 起流程 |

## 环境

- JDK 17、Maven 3.x（可用 IDEA 自带）
- MySQL 8（各环境通常本机安装；脚本见下）
- Redis：trade / portal / gateway 必依赖（process-service 主要依赖 MySQL）
- RocketMQ 4.9.x：**仅测售后站内信时需要**

**中间件两种起法（任选，不要混用同端口）：**

| 方式 | 适合 | Redis | RocketMQ |
|------|------|-------|----------|
| **A. Docker Compose** | Windows / 已装 Docker 的机器 | `docker compose up -d redis` | `docker compose up -d rocketmq-namesrv rocketmq-broker` |
| **B. 本机部署** | macOS 等本地安装习惯 | `brew` / `redis-server` | 官方 `*-bin-release` + `config-file/rocketmq/*.sh` |

应用侧统一连本机映射端口：Redis `127.0.0.1:6379`，NameServer `127.0.0.1:9876`。按本机账号密码改各服务 `application.yml`。

## 使用方法

### 1. MySQL

库名 `aftersale`，脚本为 **UTF-8**。Windows 本机 MySQL 5.7 客户端常默认 `gbk`，不加字符集会报 `Data too long` / `Incorrect string value`（中文乱码）。

**macOS / Linux（本机 mysql）：**

```bash
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/01-init.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/02-user.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/03-stock.sql
# 已有库补 SKU 商家列：mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/03b-stock-sku-merchant.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/04-aftersale.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/05-order.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/06-process.sql
# 已有售后库若缺换货列：mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/04b-aftersale-exchange-sku.sql
```

**Windows PowerShell（须 `--default-character-set=utf8mb4` + `cmd` 重定向；不要用 `Get-Content | mysql`，管道会把中文弄成 `?`）：**

```powershell
cd F:\code\aftersale-backend
$MYSQL = "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"  # 按本机路径改
$env:MYSQL_PWD = "123456"  # 与 application.yml 一致；用完可 Remove-Item Env:MYSQL_PWD

foreach ($f in @(
  "config-file\sql\01-init.sql",
  "config-file\sql\02-user.sql",
  "config-file\sql\03-stock.sql",
  "config-file\sql\04-aftersale.sql",
  "config-file\sql\05-order.sql",
  "config-file\sql\06-process.sql"
)) {
  cmd /c "`"$MYSQL`" -uroot --default-character-set=utf8mb4 < `"$f`""
}

cmd /c "`"$MYSQL`" -uroot --default-character-set=utf8mb4 aftersale -e `"SELECT id,username,role FROM sys_user; SELECT sku_id,sku_name FROM stock_sku; SELECT order_no,sku_id FROM order_info;`""
Remove-Item Env:MYSQL_PWD
```

核对时若终端显示乱码，用 `HEX(username)` 看是否为 UTF-8（如 `AAA代购` → `414141E4BBA3E8B4AD`），不要只看控制台问号。

| 脚本 | 内容 |
|------|------|
| `01` | 建库：`aftersale`（唯一库） |
| `02` | 用户中心 `sys_*` + 演示账号 |
| `03` | SKU 档案 + 库存计数 / 流水 `stock_*`（含 `merchant_id`） |
| `03b` | 已有库增量：`stock_sku.merchant_id` |
| `04` | 售后单 `aftersale_ticket` + 操作日志 + outbox |
| `05` | 模拟订单 `order_info` |
| `06` | 流程投影 `process_biz` / `process_oper_log`（`act_*` 由引擎自动建） |

演示账号（密码均为 `123456`）：

| 用户名 | 角色 | id |
|--------|------|-----|
| `00000` | ADMIN | 1 |
| `AAA代购` | MERCHANT | 2 |
| `AAA极速发货` | WAREHOUSE | 3 |
| `精神状态良好` | USER | 5 |

演示订单挂在用户 id=5、商家 id=2（见 `05-order.sql`）。表结构变更后若本地已有旧库，需重导或按 `docs/problem.md` / 历史迁移说明处理。

### 2. Redis（二选一）

用途：gateway 登出黑名单、业务锁、SKU 缓存、单号序号、售后幂等键、通知消费幂等键等。未启动时 trade-service 会因 Redisson 连不上而失败。

#### 方式 A：Docker（Windows 常用）

仓库根目录：

```bash
docker compose up -d redis
docker compose ps redis
# 或：docker exec -it aftersale-redis redis-cli ping   → PONG
```

停用：`docker compose stop redis`。

#### 方式 B：本机安装（macOS 常用）

```bash
# 安装示例（Homebrew）
brew install redis
brew services start redis   # 或前台：redis-server

redis-cli ping              # PONG
```

### 3. RocketMQ（测售后站内信时需要；二选一）

trade 默认：`rocketmq.name-server=127.0.0.1:9876`。  
**不测通知**时可不起 MQ：售后主链路仍可跑，发通知失败只打日志。

#### 方式 A：Docker Compose（Windows 常用）

```bash
# 仓库根目录；会挂载 config-file/rocketmq/broker.conf（brokerIP1=127.0.0.1，供宿主机 Producer 访问）
docker compose up -d rocketmq-namesrv rocketmq-broker
docker compose ps
# NameServer :9876，Broker :10911 / 10909 / 10912；状态应为 healthy

docker compose stop rocketmq-namesrv rocketmq-broker
```

测通知时建议：**先起 MQ，再启 / 重启 trade**，避免 Producer 连不上。

#### 方式 B：本机二进制 + 脚本（macOS 常用）

下载 **`rocketmq-all-4.9.7-bin-release.zip`**（须带 `bin-release`，不是源码树），解压到例如：

```text
~/rocketmq/rocketmq-4.9.7/
  bin/
  conf/
  lib/
```

启停（脚本默认 `RMQ_HOME=$HOME/rocketmq/rocketmq-4.9.7`，JDK8 路径见脚本内 `JAVA_HOME`）：

```bash
chmod +x config-file/rocketmq/start-local.sh config-file/rocketmq/stop-local.sh
./config-file/rocketmq/start-local.sh
# NameServer :9876，Broker :10911（冷启动可能较慢，脚本会轮询等待）

./config-file/rocketmq/stop-local.sh
```

### 4. 启动 Java 服务（IDEA）

1. 打开仓库根目录，JDK 17，Maven Reload。
2. **建议启动顺序**：
   1. MySQL + Redis（§1 / §2）  
   2. （可选）RocketMQ — 见 §3；测站内信时再起  
   3. `com.yean.portal.PortalApplication` → `:8081`  
   4. `com.yean.process.ProcessApplication` → `:8083`（售后依赖流程 RPC）  
   5. `com.yean.trade.TradeApplication` → `:8082`（若测通知，须已起 MQ）  
   6. `com.yean.gateway.GatewayApplication` → `:8080`
3. 默认 profile `local`（不注册 Nacos）。要用 Nacos：`--spring.profiles.active=nacos`，并先起 Nacos。

Swagger：

- gateway：http://127.0.0.1:8080/swagger-ui.html（登录为 RouterFunction，以 curl 为准）
- portal：http://127.0.0.1:8081/swagger-ui.html
- process：http://127.0.0.1:8083/swagger-ui.html
- trade：http://127.0.0.1:8082/swagger-ui.html

经网关访问业务接口需：`Authorization: Bearer <access>`（Access 默认约 **30 分钟**）。
Refresh 为 **HttpOnly + SameSite +（生产 Secure）Cookie**（`path=/api/auth`，默认约 **7 天**）；`POST /api/auth/refresh` 换新 Access 并**拉黑旧 Refresh**。

本地直连 trade 时可用 Header `X-User-Id` / `X-Username` / `X-Role`（`yean.operator.allow-body=true` 时也可用 body 调试字段）。


### 5. 命令行示例

```bash
# 登录（Access 在 JSON；Refresh 写入 Cookie，用 -c 保存）
curl -c /tmp/as_cookies.txt -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"00000","password":"123456"}'

# 续期（带上 Refresh Cookie；旧 Refresh 拉黑，响应 Set-Cookie 新 Refresh）
curl -b /tmp/as_cookies.txt -c /tmp/as_cookies.txt \
  -X POST http://127.0.0.1:8080/api/auth/refresh

# 用户列表（替换 data.token）
curl http://127.0.0.1:8080/user/list \
  -H 'Authorization: Bearer <access>'

# 新增订单
curl -X POST http://127.0.0.1:8080/order \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"skuId":1001,"skuName":"无线蓝牙耳机","num":1,"amount":199.00,"userId":5}'

# CSV 批量导入（orderNo 可空自动生成；压测样例见 docs/bench/orders_2000.csv）
curl -X POST http://127.0.0.1:8080/order/upload \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@docs/bench/orders_2000.csv'

# 库存查询 / 扣减（写接口须 WAREHOUSE 或 ADMIN）
curl http://127.0.0.1:8080/stock/1001 -H 'Authorization: Bearer <token>'
curl -X POST http://127.0.0.1:8080/stock/deduct \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"skuId":1001,"changeNum":1,"bizNo":"DEMO-001"}'

# 售后草稿：保存（部分字段可空；本地直连示例）
curl -X POST http://127.0.0.1:8082/aftersale/draft \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORD20250920001","skuId":1001,"type":1,"num":1,"reason":"先存着","userId":5,"username":"精神状态良好","role":"USER"}'

# 加载草稿（回填）
curl 'http://127.0.0.1:8082/aftersale/draft?orderNo=ORD20250920001&skuId=1001' \
  -H 'X-User-Id: 5' -H 'X-Username: 精神状态良好' -H 'X-Role: USER'

# 提交正式单（会先删同单草稿再新增；经网关则用 USER token）
# type=1 退货 / 3 仅退款；type=2 换货须带 exchangeSkuId
curl -X POST http://127.0.0.1:8082/aftersale/apply \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORD20250920001","skuId":1001,"type":1,"num":1,"reason":"质量问题","userId":5,"username":"精神状态良好","role":"USER"}'

# 列表默认不含草稿
curl 'http://127.0.0.1:8082/aftersale/page?page=1&size=10' \
  -H 'X-User-Id: 5' -H 'X-Username: 精神状态良好' -H 'X-Role: USER'

# 商家审核通过（退货/换货会进仓库；仅退款 type=3 则下一节点是退款）
curl -X POST http://127.0.0.1:8082/aftersale/approve \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","userId":2,"username":"AAA代购","role":"MERCHANT"}'
# 商家驳回（流程结束，status=已驳回；可写 remark）
# curl -X POST http://127.0.0.1:8082/aftersale/reject \
#   -H 'Content-Type: application/json' \
#   -d '{"aftersaleNo":"AS...","remark":"不符合退货条件","userId":2,"username":"AAA代购","role":"MERCHANT"}'

# 退货/换货：仓库发货 → 用户收货 →（退货再）商家退款
curl -X POST http://127.0.0.1:8082/aftersale/ship \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","logisticsInfo":{"company":"顺丰","trackingNo":"SF001"},"userId":3,"username":"AAA极速发货","role":"WAREHOUSE"}'
curl -X POST http://127.0.0.1:8082/aftersale/receive \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","userId":5,"username":"精神状态良好","role":"USER"}'
curl -X POST http://127.0.0.1:8082/aftersale/refund \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","userId":2,"username":"AAA代购","role":"MERCHANT"}'

# 站内信（MQ 正常且已 apply 后，商家 userId=2 应有待审通知）
curl 'http://127.0.0.1:8081/notify/list?userId=2&page=1&size=5'
```

订单 CSV 表头：

```text
orderNo,userId,merchantId,skuId,skuName,num,amount,status,payStatus
```

`orderNo` / `merchantId` / `status` / `payStatus` 可空（空 `orderNo` 自动生成；默认商家 2、状态待发货、已支付）。

### 6. Docker 一键与 Nacos（可选）

`docker-compose.yml` 提供 **Redis、RocketMQ、Nacos**。走 Docker 的机器可一次起依赖（MySQL 仍建议本机）：

```bash
# 仅中间件（推荐日常）
docker compose up -d redis rocketmq-namesrv rocketmq-broker

# 需要注册中心时再加 Nacos
docker compose up -d nacos
# Nacos http://127.0.0.1:8848/nacos  （nacos / nacos）

docker compose ps
docker compose stop          # 停全部 compose 服务
# 或：docker compose down    # 停并删容器（数据卷策略以 compose 为准）
```

macOS 若已用 brew / 本机二进制起 Redis、MQ，**不要再**对同端口 `compose up`，避免冲突。Nacos 仍可单独 `docker compose up -d nacos`。

### Sentinel 限流（gateway）

本地规则写在 `SentinelRulesConfig`（不依赖 Dashboard）：

| 资源 | 方式 | 默认阈值 | 演示 |
|------|------|----------|------|
| `/api/auth/login` | WebFilter + FlowRule | **30 QPS** | 可临时调低到 3～5 |
| `/aftersale/apply` | 自定义 API GatewayFlowRule | **50 QPS** | 可临时调低到 5 |
| 路由 `trade-service` / `portal` | 按 routeId | **500 QPS** | 可临时调低到 20～50 |

阈值改 `SentinelRulesConfig` 常量后重启 gateway。超限返回 HTTP **429**：`{"code":429,"msg":"请求过于频繁，请稍后再试","data":null}`。

```bash
# 演示前把 LOGIN_QPS 临时调低，再连打登录看部分 429
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://127.0.0.1:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"00000","password":"123456"}'
done
```

可选 Dashboard：`SENTINEL_DASHBOARD=127.0.0.1:8858` 后再启 gateway。

