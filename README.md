# aftersale-backend

Spring Cloud 电商**售后退换货** Demo：网关鉴权 + 用户中心 + 交易业务（订单 / 售后 / SKU 库存）+ Activiti 流程引擎。

技术栈：Spring Boot 2.7、Spring Cloud Gateway、OpenFeign、MyBatis-Plus、Redisson、Activiti 6、MySQL 8、Redis、RocketMQ 4.9（通知）。

## 模块与端口

```
aftersale-backend
├── common/              # 公共 jar：R、异常、角色权限、RedisBizLock、@OperateLog、Feign
├── portal/              # 用户中心 :8081（sys_* 表）
├── gateway/             # 网关鉴权 :8080（不连业务库）
├── basic/
│   ├── trade-service/   # 交易业务 :8082（订单 + 售后 + SKU/库存；Feign 调流程；发/收售后通知）
│   └── process-service/ # Activiti 流程引擎 :8083（process_* / act_*）
├── config-file/
│   ├── sql/             # 建库建表（单库 aftersale）
│   └── rocketmq/        # Broker 配置 + 本机启停脚本 start-local.sh / stop-local.sh
├── docs/                # 本地手册（默认 gitignore）：test.md / problem.md 等
└── docker-compose.yml   # 可选：Nacos、RocketMQ（本机也可用脚本起 MQ）
```

| 服务 | 端口 | 职责 |
|------|------|------|
| gateway | 8080 | JWT 登录/续期/登出、路由、注入 `X-User-*` |
| portal | 8081 | 用户 / 部门 / 通知 / 操作日志落库 |
| trade-service | 8082 | 订单、售后单、SKU 档案、库存扣减回退、售后 MQ 通知 |
| process-service | 8083 | Activiti：start/complete/reject/withdraw/rollback/history |
| RocketMQ NameServer | 9876 | 售后通知 Topic（测站内信时需要） |

`trade-service` 包结构（`com.yean.trade`）：

| 包 | 含义 | 表 |
|----|------|-----|
| `order` | 模拟订单 | `order_info` |
| `ticket` | 售后单 + `strategy`/`rpc`（Feign 流程） | `aftersale_ticket` 等 |
| `sku` | SKU 档案 | `stock_sku` |
| `inventory` | 库存计数 / 流水 | `stock_count` / `stock_flow` |
| `notify` | 售后通知：直发 MQ → Consumer → portal 站内信（阶段 8a） | — |
| `message` | 本地消息 outbox（表已有；**8b** 再接 Sender） | `local_message` |

`common`：`dto` / `rpc` / `config` + `enums` / `exception` / `result` / `lock`。  
全项目共用 MySQL 库 **`aftersale`**，用表名区分域；portal / trade / process 均连同一库。

## 售后流程（已可推进）

用户 `apply` → trade 落库并 Feign `process/start`，停在 **商家审核**。商家查待审核列表（或详情）后：

- **`approve`**：按类型走不同 BPMN 通道  
- **`reject`**（可带 `remark`）：流程结束，售后单 **已驳回**（`active_flag` 释放，用户可重新申请；不是回到「用户待改」节点）  
- **`withdraw`**：仅待审核，申请人撤回 → **已撤回**

| type | 含义 | 审核通过后节点 |
|------|------|----------------|
| 1 | 退货 | 仓库发货 → 用户收货（旧品回库）→ 商家退款 → 办结 |
| 2 | 换货（须 `exchangeSkuId`） | 仓库发货（扣新 SKU）→ 用户收货（旧品回库）→ 办结 |
| 3 | 仅退款 | 商家退款 → 办结（**不经仓库**） |

角色：审核/退款=`MERCHANT`（且商家归属）、发货=`WAREHOUSE`、收货=申请人 `USER`。`DONE`/`REJECTED`/`CANCELED` 后再改会被拒绝。

通知（阶段 **8a**）：节点流转主链路成功后直发 RocketMQ Topic `aftersale_notify` → trade Consumer → portal `/notify/send` 写 `sys_notify`。发送失败只打日志、不回滚业务（**无 outbox 补投**；8b 再用 `local_message`）。测通知步骤见本地 `docs/test.md` §7。

售后状态码：`0待审核 → 1待仓库发货 → 2待用户收货 → 3待退款 → 4已办结`（`5已驳回` / `6已撤回`）。

## 能力概览

- **portal**：用户部门 CRUD、多角色、登录校验（不签发 Token）、站内通知、接收各服务操作日志
- **gateway**：签发/校验 JWT、Redis 黑名单登出、白名单、身份 Header 转发下游
- **trade / order**：列表、详情、单笔新增、CSV 批量上传
- **trade / ticket**：Feign 驱动 Activiti；`TicketNodeStrategyFactory.getStrategy(action)` 同步状态/库存
- **process-service**：BPMN、候选组、终态守卫、`process_oper_log`、回退上一节点
- **trade / sku + inventory**：SKU CRUD；扣减/回退（幂等 + 锁 + CAS）；写接口限 WAREHOUSE/ADMIN
- **trade / notify**：售后事件 → RocketMQ → 站内信（8a）；outbox 待 8b
- **统一返回**：`{code, msg, data}`，分页 `PageResult`，错误码 `ErrorEnum`

## 接口前缀（经 gateway，也可直连服务端口）

| 模块 | 路径 |
|------|------|
| 登录登出 | `/api/auth/**`（仅 gateway） |
| 用户 / 部门 / 通知 / 操作日志 | `/user/**` `/dept/**` `/notify/**` `/operatelog/**` → portal |
| 订单 | `/order/**` → trade |
| 售后 | `/aftersale/**` → trade（含 `/aftersale/rollback`） |
| SKU | `/sku/**` → trade |
| 库存 | `/stock/**` → trade |
| 流程（调试） | `/process/**` → process-service（主路径为 trade Feign） |

## 环境

- JDK 17、Maven 3.x（可用 IDEA 自带）
- MySQL 8、Redis（trade / portal / gateway 依赖 Redis；process-service 主要依赖 MySQL）
- 测站内信时：本机 RocketMQ 4.9.x（**二进制包** `rocketmq-all-4.9.7-bin-release`）或 Docker Compose

按本机账号密码改各服务 `application.yml`。

## 使用方法

### 1. MySQL

```bash
mysql -uroot -p < config-file/sql/01-init.sql
mysql -uroot -p < config-file/sql/02-user.sql
mysql -uroot -p < config-file/sql/03-stock.sql
mysql -uroot -p < config-file/sql/04-aftersale.sql
mysql -uroot -p < config-file/sql/05-order.sql
mysql -uroot -p < config-file/sql/06-process.sql
# 已有售后库若缺换货列：mysql -uroot -p < config-file/sql/04b-aftersale-exchange-sku.sql
```

| 脚本 | 内容 |
|------|------|
| `01` | 建库：`aftersale`（唯一库） |
| `02` | 用户中心 `sys_*` + 演示账号 |
| `03` | SKU 档案 + 库存计数 / 流水 `stock_*` |
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

### 2. Redis

```bash
redis-server
redis-cli ping    # PONG
```

用途：gateway 登出黑名单、业务锁、SKU 缓存、单号序号、售后幂等键、通知消费幂等键等。未启动时 trade-service 会因 Redisson 连不上而失败。

### 3. RocketMQ（测售后站内信时需要）

推荐本机二进制（无需 Docker）。下载 **`rocketmq-all-4.9.7-bin-release.zip`**（须带 `bin-release`，不是源码树），解压到例如：

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

trade 默认：`rocketmq.name-server=127.0.0.1:9876`。  
**不测通知**时可不起 MQ：售后主链路仍可跑，发通知失败只打日志。

也可用 Compose（需本机 Docker）：`docker compose up -d rocketmq-namesrv rocketmq-broker`。

### 4. 启动 Java 服务（IDEA）

1. 打开仓库根目录，JDK 17，Maven Reload。
2. **建议启动顺序**：
   1. （可选）RocketMQ — 见上一节  
   2. `com.yean.portal.PortalApplication` → `:8081`  
   3. `com.yean.process.ProcessApplication` → `:8083`（售后依赖流程 RPC）  
   4. `com.yean.trade.TradeApplication` → `:8082`（若测通知，须已起 MQ）  
   5. `com.yean.gateway.GatewayApplication` → `:8080`
3. 默认 profile `local`（不注册 Nacos）。要用 Nacos：`--spring.profiles.active=nacos`，并先起 Nacos。

Swagger：

- gateway：http://127.0.0.1:8080/swagger-ui.html（登录为 RouterFunction，以 curl 为准）
- portal：http://127.0.0.1:8081/swagger-ui.html
- process：http://127.0.0.1:8083/swagger-ui.html
- trade：http://127.0.0.1:8082/swagger-ui.html

经网关访问业务接口需：`Authorization: Bearer <token>`。本地直连 trade 时可用 Header `X-User-Id` / `X-Username` / `X-Role`（`yean.operator.allow-body=true` 时也可用 body 调试字段）。

更完整的联调步骤（登录、库存、售后闭环、通知 A/B 测）见本地 **`docs/test.md`**。

### 5. 命令行示例

```bash
# 登录
curl -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"00000","password":"123456"}'

# 用户列表（替换 token）
curl http://127.0.0.1:8080/user/list \
  -H 'Authorization: Bearer <token>'

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

# 售后：用户申请（本地直连示例；经网关则用 USER 账号登录拿 token）
# type=1 退货 / 3 仅退款；type=2 换货须带 exchangeSkuId
curl -X POST http://127.0.0.1:8082/aftersale/apply \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORD20250920001","skuId":1001,"type":1,"num":1,"reason":"质量问题","userId":5,"username":"精神状态良好","role":"USER"}'

curl 'http://127.0.0.1:8082/aftersale/page?page=1&size=10&userId=5'

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

### 6. Docker / Nacos（可选）

日常开发：**MySQL + Redis 本机必启**；RocketMQ 优先用 §3 本机脚本。  
`docker-compose.yml` 可起 **Nacos、RocketMQ**（需已装 Docker）：

```bash
docker compose up -d nacos
# 或一并起 MQ：docker compose up -d
# Nacos http://127.0.0.1:8848/nacos  （nacos / nacos）
# RocketMQ NameServer :9876

docker compose down
```

## 说明与待办

- 流程推进 + 退货/换货库存联动 **已落地**；须先起 `process-service` 再起 `trade`。
- 售后通知 **8a 已落地**（直发 MQ → 站内信）；**8b outbox**（`local_message` + Sender 补投）未做。
- 联调步骤：本地 `docs/test.md`；排错 / 设计记录：本地 `docs/problem.md`；实现计划：[`plan.md`](plan.md)。
