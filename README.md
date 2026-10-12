# aftersale-backend

<p align="center">
  <b>English</b> | <a href="./README.zh-CN.md">中文</a>
</p>

Spring Cloud backend for e-commerce **after-sales return / exchange / refund**: gateway auth + user portal + trade domain (orders / tickets / SKU inventory) + Activiti process engine.

Stack: Spring Boot 2.7, Spring Cloud Gateway, OpenFeign, MyBatis-Plus, Redisson, Activiti 6, MySQL 8, Redis, RocketMQ 4.9 (notifications).

Companion frontend: [happierupupup/aftersale-frontend](https://github.com/happierupupup/aftersale-frontend) — Vue3 + TS + Vite5 + Element Plus; in development it proxies to gateway `:8080`.

## Modules & ports

```
aftersale-backend/
├── common/                 # shared jar: R, exceptions, roles, RedisBizLock, @OperateLog, Feign
├── portal/                 # user center :8081 (sys_* tables)
├── gateway/                # auth gateway :8080 (no business DB)
├── basic/
│   ├── trade-service/      # trade :8082 (orders + tickets + SKU/stock; Feign process; notify)
│   └── process-service/    # Activiti :8083
├── config-file/
│   ├── sql/                # schema scripts (single DB aftersale)
│   └── rocketmq/           # local start/stop scripts
├── docs/                   # local notes (gitignored by default): plan / test / problem, etc.
└── docker-compose.yml
```

| Service | Port | Role |
|---------|------|------|
| gateway | 8080 | JWT login / refresh / logout, routing, inject `X-User-*` |
| portal | 8081 | users / depts / notifications / operate-log persistence |
| trade-service | 8082 | orders, after-sale tickets, SKU, stock deduct/rollback, after-sale MQ notify |
| process-service | 8083 | Activiti: start / complete / reject / withdraw / rollback / history |
| MySQL | 3306 | business DB `aftersale` (shared by portal / trade / process) |
| Redis | 6379 | locks / cache / JWT blacklist / idempotency keys (required) |
| RocketMQ NameServer | 9876 | after-sale notify topic (needed when testing in-app messages) |

`trade-service` packages (`com.yean.trade`):

| Package | Meaning | Tables |
|---------|---------|--------|
| `order` | sample orders | `order_info` |
| `ticket` | after-sale tickets + `strategy` / `rpc` (Feign process) | `aftersale_ticket`, etc. |
| `sku` | SKU master data | `stock_sku` |
| `inventory` | stock counters / ledger | `stock_count` / `stock_flow` |
| `notify` | after-sale notify: outbox → MQ → Consumer → portal (phase 8b) | — |
| `message` | local message outbox + poller | `local_message` |

`common`: `dto` / `rpc` / `config` + `enums` / `exception` / `result` / `lock`.  
All services share MySQL database **`aftersale`**, separated by table domains; portal / trade / process connect to the same DB.

## After-sale flow (ready to drive)

Apply page has two actions:

| UI button | API | Behavior |
|-----------|-----|----------|
| **Save** | `POST /aftersale/draft` | upsert draft for same user + order + SKU; `status=-1`, `active_flag=NULL`; **no process start**; invisible in merchant list |
| **Submit** | `POST /aftersale/apply` | **delete matching draft first**, then create formal ticket + Feign `process/start`, lands on merchant review |

Prefill on apply page: `GET /aftersale/draft?orderNo=&skuId=` (or `/draft/mine`); check **`draft: true`** / `status: -1` in the response.

Formal ticket progression — merchant opens pending list (or detail), then:

- **`approve`**: different BPMN paths by type  
- **`reject`** (optional `remark`): process ends → **rejected** (releases `active_flag`, can re-apply)  
- **`withdraw`**: pending review or pending warehouse ship (before ship) → **withdrawn**

| type | Meaning | After merchant approve |
|------|---------|------------------------|
| 1 | return | warehouse ship → user receive (old SKU restock) → merchant refund → done |
| 2 | exchange (requires `exchangeSkuId`) | warehouse ship (deduct new SKU) → user receive (old SKU restock) → done |
| 3 | refund-only | merchant refund → done (**no warehouse**) |

Roles: review/refund = `MERCHANT` (and merchant ownership), ship = `WAREHOUSE`, receive = applicant `USER`. Mutations after `DONE` / `REJECTED` / `CANCELED` are rejected (terminal read-only). List/detail are role-scoped (USER self, MERCHANT own merchant).

Status codes:

```text
-1 draft → (submit) 0 pending review → 1 pending warehouse ship → 2 pending user receive → 3 pending refund → 4 done
                              ↘ 5 rejected / 6 withdrawn
```

`/aftersale/page` **excludes drafts by default** (`status >= 0`); pass `status=-1` to query drafts.

Notifications (phase **8b**): node transition and `local_message(NEW)` in the **same transaction** → Sender publishes topic `aftersale_notify` → Consumer → portal `/notify/send`. On apply, **`merchant_id` comes from `stock_sku`**. See local `docs/test.md` §7 for notify testing.

Reminders: daily **10:00** scan pending review / ship / refund; manual `POST /aftersale/job/pending-remind`.  
Timeout close: daily **2:30** close tickets stuck on those nodes longer than 48h since create; manual `POST /aftersale/job/timeout`.

## Capability overview

- **portal**: user/dept CRUD, multi-role, login validation (does not issue tokens), in-app notify, operate-log persistence
- **gateway**: dual JWT (short Access + HttpOnly Refresh Cookie; refresh rotates and blacklists old Refresh), Redis blacklist logout, whitelist, **path-level role ACL**, **Sentinel rate limit**, inject `X-User-*`
- **trade / order**: list, detail, single create, CSV batch upload
- **trade / ticket**: draft save/load/delete; `apply` submit; Feign-driven Activiti; strategies sync status/stock; data scope + terminal read-only
- **process-service**: BPMN, candidate groups, terminal guards, `process_oper_log`, rollback to previous node
- **trade / sku + inventory**: SKU CRUD; deduct/rollback (idempotent + lock + CAS); write APIs limited to WAREHOUSE/ADMIN
- **trade / notify + message**: after-sale events → outbox → RocketMQ → in-app message
- **Unified response**: `{code, msg, data}`, paging `PageResult`, errors `ErrorEnum`

## Key design

- **Dual JWT + Redis blacklist**: short Access (Bearer) + long Refresh (HttpOnly Cookie); refresh rotates and blacklists old `jti`; downstream trusts only `X-User-*`; multi-gateway shared login state.
- **Process isolation + node strategies**: Activiti only in `process-service`; after trade Feign advances a node, `TicketNodeStrategy` runs (ship deducts new SKU, receive restocks old, etc.) — BPMN defines path, strategies define side effects.
- **Persist ticket then async process start**: apply writes ticket + `process_start_job` in one TX (no Feign in-request); poller / after-commit race starts `process/start`, then backfills `processInstanceId` and publishes apply notify; approve before start returns `PROCESS_STARTING`.
- **Draft vs in-progress slot**: same-table draft `status=-1`, `active_flag=NULL`, no process; **submit deletes draft then creates formal ticket**; in-progress uniqueness on `(order_no, sku_id, active_flag)`; reject/withdraw frees the slot for re-apply.
- **Cross-service eventual consistency**: main state sync (process RPC + local TX); in-app notify via outbox (`local_message`) → RocketMQ → idempotent consume; if process advanced but local failed, write `process_compensate` and poll-retry rollback; process-start failures after max retry cancel the ticket and free the slot.
- **Inventory trio**: Redisson lock serializes entry + DB `version` CAS against oversell + unique key on `stock_flow` for idempotency; SKU Cache Aside with **delayed double-delete** after write.
- **Multi-instance safety**: scheduled remind / timeout / outbox poller use Redisson `tryLock`; apply and review ops use Redis SET NX for idempotency.
- **Scheduled jobs**: outbox every 5s; process compensate every 10s on `process_compensate`; remind daily 10:00; timeout close daily 2:30 (close if created > 48h and still stuck).
- **Side thread pools**: `@OperateLog` fixed pool async persist (off critical path); SKU delayed double-delete uses a single-thread `ScheduledExecutor` (order CSV batch remains synchronous).
- **Observability**: business logs `sys_operate_log` / `aftersale_oper_log` / `process_oper_log`; tracing **`X-Trace-Id`** (gateway generates or forwards → Feign/WebClient; log MDC `%X{traceId}`).

## API prefixes (via gateway; services also reachable on their ports)

| Module | Path |
|--------|------|
| Login / refresh / logout | `/api/auth/login` · `/refresh` · `/logout` (gateway only; Refresh via Cookie) |
| User / dept / notify / operate-log | `/user/**` `/dept/**` `/notify/**` `/operatelog/**` → portal |
| Orders | `/order/**` → trade |
| After-sale | `/aftersale/**` → trade (incl. draft / apply / rollback / job) |
| Local message | `/message/job/**` → trade (outbox poller) |
| SKU | `/sku/**` → trade |
| Stock | `/stock/**` → trade |
| Process (debug) | `/process/**` → process-service (main path is trade Feign) |

### After-sale draft / submit

| Method | Path | Notes |
|--------|------|-------|
| POST | `/aftersale/draft` | save draft (some fields nullable; upsert by user+order+SKU) |
| GET | `/aftersale/draft?orderNo=&skuId=` | load current user's draft for that order line; `data=null` if none |
| GET | `/aftersale/draft/mine` | all drafts for current user |
| DELETE | `/aftersale/draft/{aftersaleNo}` | delete draft |
| POST | `/aftersale/apply` | **submit**: delete draft then create formal ticket + start process |

## Environment

- JDK 17, Maven 3.x (IDEA bundled Maven is fine)
- MySQL 8 (usually installed locally; scripts below)
- Redis: required by trade / portal / gateway (process-service mainly needs MySQL)
- RocketMQ 4.9.x: **only required when testing after-sale in-app notify**

**Two ways to run middleware (pick one; do not mix the same ports):**

| Mode | Good for | Redis | RocketMQ |
|------|----------|-------|----------|
| **A. Docker Compose** | Windows / machines with Docker | `docker compose up -d redis` | `docker compose up -d rocketmq-namesrv rocketmq-broker` |
| **B. Native install** | macOS local habits | `brew` / `redis-server` | official `*-bin-release` + `config-file/rocketmq/*.sh` |

Apps connect to host-mapped ports: Redis `127.0.0.1:6379`, NameServer `127.0.0.1:9876`. Align each service `application.yml` with your local credentials.

## Getting started

### 1. MySQL

Database name `aftersale`. Scripts are **UTF-8**. Windows MySQL 5.7 clients often default to `gbk`; without charset you may hit `Data too long` / `Incorrect string value` (mojibake).

**macOS / Linux (local mysql):**

```bash
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/01-init.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/02-user.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/03-stock.sql
# existing DB missing SKU merchant column:
# mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/03b-stock-sku-merchant.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/04-aftersale.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/05-order.sql
mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/06-process.sql
# existing after-sale DB missing exchange SKU column:
# mysql -uroot -p --default-character-set=utf8mb4 < config-file/sql/04b-aftersale-exchange-sku.sql
```

**Windows PowerShell (must use `--default-character-set=utf8mb4` + `cmd` redirect; do not use `Get-Content | mysql` — the pipe can turn Chinese into `?`):**

```powershell
cd F:\code\aftersale-backend
$MYSQL = "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"  # adjust path
$env:MYSQL_PWD = "123456"  # match application.yml; Remove-Item Env:MYSQL_PWD when done

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

If the terminal shows garbled text, check `HEX(username)` for real UTF-8 (e.g. `AAA代购` → `414141E4BBA3E8B4AD`) instead of trusting console `?`.

| Script | Contents |
|--------|----------|
| `01` | create DB `aftersale` (single database) |
| `02` | user center `sys_*` + seed accounts |
| `03` | SKU + stock count / ledger `stock_*` (incl. `merchant_id`) |
| `03b` | incremental: `stock_sku.merchant_id` |
| `04` | after-sale ticket `aftersale_ticket` + oper log + outbox |
| `05` | sample orders `order_info` |
| `06` | process projection `process_biz` / `process_oper_log` (`act_*` created by engine) |

Seed accounts (password `123456` for all):

| Username | Role | id |
|-------------|------|-----|
| `admin`     | ADMIN | 1 |
| `merchant`  | MERCHANT | 2 |
| `warehouse` | WAREHOUSE | 3 |
| `user`      | USER | 5 |

Sample orders belong to user id=5, merchant id=2 (see `05-order.sql`). After schema changes, re-import or follow `docs/problem.md` / historical migration notes.

### 2. Redis (pick one)

Used for: gateway logout blacklist, business locks, SKU cache, sequence numbers, after-sale idempotency keys, notify consume idempotency, etc. Without Redis, trade-service fails on Redisson connect.

#### Mode A: Docker (common on Windows)

From repo root:

```bash
docker compose up -d redis
docker compose ps redis
# or: docker exec -it aftersale-redis redis-cli ping   → PONG
```

Stop: `docker compose stop redis`.

#### Mode B: Native install (common on macOS)

```bash
# Homebrew example
brew install redis
brew services start redis   # or foreground: redis-server

redis-cli ping              # PONG
```

### 3. RocketMQ (needed for after-sale in-app notify; pick one)

trade default: `rocketmq.name-server=127.0.0.1:9876`.  
**If not testing notify**, you can skip MQ: main after-sale path still works; notify failures are logged only.

#### Mode A: Docker Compose (common on Windows)

```bash
# repo root; mounts config-file/rocketmq/broker.conf (brokerIP1=127.0.0.1 for host Producer)
docker compose up -d rocketmq-namesrv rocketmq-broker
docker compose ps
# NameServer :9876, Broker :10911 / 10909 / 10912; should be healthy

docker compose stop rocketmq-namesrv rocketmq-broker
```

When testing notify: **start MQ first, then start / restart trade**, so the Producer can connect.

#### Mode B: Native binary + scripts (common on macOS)

Download **`rocketmq-all-4.9.7-bin-release.zip`** (must be `bin-release`, not the source tree), extract e.g.:

```text
~/rocketmq/rocketmq-4.9.7/
  bin/
  conf/
  lib/
```

Start/stop (scripts default `RMQ_HOME=$HOME/rocketmq/rocketmq-4.9.7`; JDK8 path in script `JAVA_HOME`):

```bash
chmod +x config-file/rocketmq/start-local.sh config-file/rocketmq/stop-local.sh
./config-file/rocketmq/start-local.sh
# NameServer :9876, Broker :10911 (cold start may be slow; script polls)

./config-file/rocketmq/stop-local.sh
```

### 4. Start Java services (IDEA)

1. Open repo root, JDK 17, Maven Reload.
2. **Suggested order**:
   1. MySQL + Redis (§1 / §2)  
   2. (optional) RocketMQ — §3; start when testing in-app notify  
   3. `com.yean.portal.PortalApplication` → `:8081`  
   4. `com.yean.process.ProcessApplication` → `:8083` (after-sale depends on process RPC)  
   5. `com.yean.trade.TradeApplication` → `:8082` (MQ must be up if testing notify)  
   6. `com.yean.gateway.GatewayApplication` → `:8080`
3. Default profile `local` (no Nacos). For Nacos: `--spring.profiles.active=nacos`, start Nacos first.

Swagger:

- gateway: http://127.0.0.1:8080/swagger-ui.html (login is RouterFunction; prefer curl)
- portal: http://127.0.0.1:8081/swagger-ui.html
- process: http://127.0.0.1:8083/swagger-ui.html
- trade: http://127.0.0.1:8082/swagger-ui.html

Business APIs via gateway need: `Authorization: Bearer <access>` (Access default ~**30 minutes**).  
Refresh is **HttpOnly + SameSite + (Secure in prod) Cookie** (`path=/api/auth`, default ~**7 days**); `POST /api/auth/refresh` issues new Access and **blacklists old Refresh**.

When calling trade directly locally, you may pass headers `X-User-Id` / `X-Username` / `X-Role` (or body debug fields when `yean.operator.allow-body=true`).

### 5. CLI examples

```bash
# Login (Access in JSON; Refresh set as Cookie — save with -c)
curl -c /tmp/as_cookies.txt -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"00000","password":"123456"}'

# Refresh (send Refresh Cookie; old Refresh blacklisted; Set-Cookie new Refresh)
curl -b /tmp/as_cookies.txt -c /tmp/as_cookies.txt \
  -X POST http://127.0.0.1:8080/api/auth/refresh

# User list (replace with data.token / access)
curl http://127.0.0.1:8080/user/list \
  -H 'Authorization: Bearer <access>'

# Create order
curl -X POST http://127.0.0.1:8080/order \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"skuId":1001,"skuName":"Wireless Bluetooth Earbuds","num":1,"amount":199.00,"userId":5}'

# CSV batch import (empty orderNo auto-generated; load sample: docs/bench/orders_2000.csv)
curl -X POST http://127.0.0.1:8080/order/upload \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@docs/bench/orders_2000.csv'

# Stock query / deduct (writes require WAREHOUSE or ADMIN)
curl http://127.0.0.1:8080/stock/1001 -H 'Authorization: Bearer <token>'
curl -X POST http://127.0.0.1:8080/stock/deduct \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"skuId":1001,"changeNum":1,"bizNo":"DEMO-001"}'

# After-sale draft: save (some fields nullable; direct trade example)
curl -X POST http://127.0.0.1:8082/aftersale/draft \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORD20250920001","skuId":1001,"type":1,"num":1,"reason":"save for later","userId":5,"username":"精神状态良好","role":"USER"}'

# Load draft (prefill)
curl 'http://127.0.0.1:8082/aftersale/draft?orderNo=ORD20250920001&skuId=1001' \
  -H 'X-User-Id: 5' -H 'X-Username: 精神状态良好' -H 'X-Role: USER'

# Submit formal ticket (deletes matching draft first; via gateway use USER token)
# type=1 return / 3 refund-only; type=2 exchange requires exchangeSkuId
curl -X POST http://127.0.0.1:8082/aftersale/apply \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"ORD20250920001","skuId":1001,"type":1,"num":1,"reason":"quality issue","userId":5,"username":"精神状态良好","role":"USER"}'

# List excludes drafts by default
curl 'http://127.0.0.1:8082/aftersale/page?page=1&size=10' \
  -H 'X-User-Id: 5' -H 'X-Username: 精神状态良好' -H 'X-Role: USER'

# Merchant approve (return/exchange → warehouse; refund-only type=3 → refund node)
curl -X POST http://127.0.0.1:8082/aftersale/approve \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","userId":2,"username":"AAA代购","role":"MERCHANT"}'
# Merchant reject (process ends, status=rejected; optional remark)
# curl -X POST http://127.0.0.1:8082/aftersale/reject \
#   -H 'Content-Type: application/json' \
#   -d '{"aftersaleNo":"AS...","remark":"does not meet return policy","userId":2,"username":"AAA代购","role":"MERCHANT"}'

# Return/exchange: warehouse ship → user receive → (return then) merchant refund
curl -X POST http://127.0.0.1:8082/aftersale/ship \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","logisticsInfo":{"company":"SF Express","trackingNo":"SF001"},"userId":3,"username":"AAA极速发货","role":"WAREHOUSE"}'
curl -X POST http://127.0.0.1:8082/aftersale/receive \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","userId":5,"username":"精神状态良好","role":"USER"}'
curl -X POST http://127.0.0.1:8082/aftersale/refund \
  -H 'Content-Type: application/json' \
  -d '{"aftersaleNo":"AS...","userId":2,"username":"AAA代购","role":"MERCHANT"}'

# In-app notify (with MQ up and after apply, merchant userId=2 should see pending-review notify)
curl 'http://127.0.0.1:8081/notify/list?userId=2&page=1&size=5'
```

Order CSV header:

```text
orderNo,userId,merchantId,skuId,skuName,num,amount,status,payStatus
```

`orderNo` / `merchantId` / `status` / `payStatus` may be empty (empty `orderNo` auto-generated; default merchant 2, status pending ship, paid).

### 6. Docker one-shot & Nacos (optional)

`docker-compose.yml` provides **Redis, RocketMQ, Nacos**. On Docker machines you can bring up deps in one go (MySQL still recommended local):

```bash
# middleware only (typical day-to-day)
docker compose up -d redis rocketmq-namesrv rocketmq-broker

# add Nacos when you need a registry
docker compose up -d nacos
# Nacos http://127.0.0.1:8848/nacos  (nacos / nacos)

docker compose ps
docker compose stop          # stop all compose services
# or: docker compose down    # stop and remove containers (volumes per compose file)
```

On macOS, if Redis/MQ already run via brew / native binaries, **do not** `compose up` the same ports. Nacos can still be started alone: `docker compose up -d nacos`.

### Sentinel rate limiting (gateway)

Local rules live in `SentinelRulesConfig` (no Dashboard required):

| Resource | Mechanism | Default QPS | For demos |
|----------|-----------|-------------|-----------|
| `/api/auth/login` | WebFilter + FlowRule | **30 QPS** | temporarily lower to 3–5 |
| `/aftersale/apply` | custom API GatewayFlowRule | **50 QPS** | temporarily lower to 5 |
| routes `trade-service` / `portal` | by routeId | **500 QPS** | temporarily lower to 20–50 |

Change constants in `SentinelRulesConfig` and restart gateway. Over limit → HTTP **429**: `{"code":429,"msg":"请求过于频繁，请稍后再试","data":null}`.

```bash
# temporarily lower LOGIN_QPS, then hammer login to see some 429s
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://127.0.0.1:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"username":"00000","password":"123456"}'
done
```

Optional Dashboard: set `SENTINEL_DASHBOARD=127.0.0.1:8858` before starting gateway.
