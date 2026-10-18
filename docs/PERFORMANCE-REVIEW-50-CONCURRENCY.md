# 50 并发性能审查（E5 4c8g）

审查对象：`knowledge-match`（Spring Boot 3.4 + Vue/React 前端 + PostgreSQL + Redis + MinIO + OpenResty）
部署形态：`deploy/run-api.sh`（systemd，127.0.0.1:8082）+ 1Panel OpenResty + 本机 PG/Redis。

## 一、结论

**50 并发对 4c8g 本身不算高，但这个项目有几处"放大器"，会把 50 个真人用户放大成每秒上千次 SQL 和重度 JSON 序列化。**

按影响排序，真正需要在活动前处理的是这 4 条：

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| P0-1 | 广播风暴：任意事件 → 每个客户端 6 个并行请求 | `src/App.jsx:5932,5997,7418` | 50 人 → 单次作答产生 ~300 个 HTTP 请求 |
| P0-2 | 每次作答在请求线程里同步做全量大屏重算 | `service/SubmissionScreenUpdater.java:33` | 每次作答 = 全量 scoreboard + N+1 查询 + 设备广播 |
| P0-3 | `screenResponses` N+1 查询 | `service/ActivityService.java:896` | 每次作答额外 N 次 `participants.findById` |
| P0-4 | 连接池/线程池/JVM 全是默认值 | `application.yml`、`run-api.sh:26` | Hikari 10 连接 = 硬顶；`java -jar` 无 `-Xmx` |

一句话：**瓶颈不在"50 并发"本身，而在于一次作答被放大成了上百次查询和上百次推送。**

---

## 二、容量模型（量化）

场景：50 名参与者 + 2 块大屏（RESULT/SCOREBOARD 模式）+ 2 台工作人员设备，同题 25 秒答题窗口。

### 单次答题提交的实际开销（从代码逐行数出来）

主事务 `ActivityService.submitAnswer`（`ActivityService.java:607-664`）：

```
activities.findById                  (requireActivity)
answer_submissions  idempotency 查询
participants.findByIdForUpdate       ← 悲观写锁
questions.findForAnswer
answer_submissions  重复作答查询
question_sets       活跃题库查询
question_set_items  全量条目查询
answer_submissions  COUNT            ← 算 responseRank
INSERT answer_submissions
INSERT score_ledgers
UPDATE participants
```

afterCommit 事务 `SubmissionScreenUpdater.submitted`（`SubmissionScreenUpdater.java:33-50`）：

```
activities.findById                        (requireActivity)
screen_devices.findForDisplayUpdate
answer_submissions COUNT
screen_devices.findForDisplayUpdate        ← 再来一遍
activities.findById                        (scoreboard → requireActivity)
participants.findByActivityId              ← 全量参与者
questions.findById                         (screenResponses)
answer_submissions.findByActivityIdAndQuestionId
participants.findById  × N                 ← N+1，N = 已作答人数
UPDATE screen_devices × 设备数
```

**单次提交 ≈ 10 次写/读 + N 次 N+1 查询（N 为已作答人数）**
第 50 个提交者身上就是 ~60 次查询。

### 50 人一题的总量

- 服务端直接开销：≈ 8 + 50 + 4 ≈ **60 查询 × 50 人 ≈ 3000 查询**
- 客户端放大：每次作答广播 `answer.scored`，50 个客户端各收到 → 各发 6 个请求 → **300 个 HTTP 请求**
- 这 300 个请求的鉴权开销（见 P1-1）：每请求 5 次 SQL → **1500 查询**
- 300 个请求里有 50 个是 `/scoreboard`，每个都全量加载参与者再 Java 排序

**合计一题 ≈ 5000+ 查询 + 50 次 500KB 级 JSON 序列化。**

如果作答集中在 2~3 秒（真实活动就是这样），峰值 ≈ **1500~2000 QPS**，落在 4 核、且同机还跑着 PG + Redis + MinIO + OpenResty。这就是会卡的地方。

---

## P0-1 广播风暴：每个事件触发每个客户端 6 个并行请求

**证据**

`src/App.jsx:7394-7440`（`useActivityStream`）：

```js
const reload = (message) => {
  ...
  if (pending) return;
  pending = window.setTimeout(() => { pending = null; onEvent(); }, 200);   // 7416
};
client.subscribe(`/topic/activities/${activityId}`, reload);                // 7418
```

`src/App.jsx:5961-5971`（参与者页面的 `load()`）：

```js
const [questionList, control, board, answerList, activityInfo, siteInfo] =
  await Promise.all([
    api.questions(...),       // 每次事件都重拉
    api.controlState(...),    // 每次事件都重拉
    api.scoreboard(...),      // 每次事件都重拉
    api.submissions(...),     // 每次事件都重拉
    api.activity(...),        // 每次事件都重拉（几乎不变）
    api.siteSettings(),       // 每次事件都重拉（几乎不变）
  ]);
```

而且参与者页面订阅了 **两条** 流（`App.jsx:5997-5998`），两条流各自独立防抖，一次广播可能触发 2 次 `load()` → 12 个请求。

**放大器链条**

`ActivityService.submitAnswer:660` → `broadcast(activityId, "answer.scored")`
→ `RealtimeEventBus.send` → Redis pub/sub → 本节点 `receive` → 推给该 topic 全部 50 个订阅者
→ 每个订阅者 200ms 后发 6 个请求。

`broadcast()`（`ActivityService.java:1743-1757`）还会向 `parentActivityId` 和所有共享参与者子活动额外发 `score.updated`，进一步放大。

**修复**

1. **事件分流**：`answer.scored` / `score.updated` 只让客户端刷新 scoreboard 与自己的 submissions；`activity`、`siteSettings`、`questions` 这类准静态数据只在挂载时拉一次。

```js
// useActivityStream 的回调带上事件类型
const reload = (message) => {
  let type = "";
  try { type = JSON.parse(message.body)?.type || ""; } catch { /* 容错 */ }
  if (type === "screen.device.heartbeat") return;
  if (pending) return;
  pending = window.setTimeout(() => { pending = null; onEvent(type); }, 300);  // 200 → 300
};
```

2. **拆分 `load()`**：`loadStatic()`（activity + siteSettings + questions，仅挂载 / 题目集变更时）+ `loadLive()`（controlState + scoreboard + submissions）。

3. **合并两条流**：`App.jsx:5997-5998` 用一个 `useActivityStream` 订阅 `[activityId, identityActivityId]`，共享同一个防抖计时器。

---

## P0-2 每次作答在请求线程里同步做全量大屏重算

**证据** `SubmissionScreenUpdater.java:33-50`：

```java
@TransactionalEventListener                    // 默认同步，跑在提交请求的线程里
public void submitted(Submitted event) {
  transaction.executeWithoutResult(status -> {  // REQUIRES_NEW
    screens.refreshQuestionSubmissionCount(...);   // 查设备 + 每设备 2 条广播
    refreshLiveData(...);                          // 全量 scoreboard + N+1 + 每设备 2 条广播
  });
}
```

全项目 `grep @Async|EnableAsync|TaskExecutor` = **0 命中**，说明没有任何异步/线程池。

后果：第 50 个提交者的 HTTP 响应要等自己那次全量 scoreboard 重算完成。50 人同时提交 = 50 次全量重算串行抢 4 个核。

另外 `ScreenService.broadcastDevice`（`ScreenService.java:507-511`）每台设备发 **2 条** 消息（活动 topic + 设备 topic），payload 里带完整 scoreboard：

```java
private void broadcastDevice(UUID activityId, ScreenDevice device, String type, Object payload) {
  ScreenEvent event = new ScreenEvent(type, "DEVICE", payload, Instant.now());
  realtime.send("/topic/activities/" + activityId + "/screens", event);
  realtime.send("/topic/screens/" + device.getId(), event);      // 重复一份
}
```

**修复**

1. **异步 + 合并**：把大屏刷新改成 `@Async` + 每活动 200~500ms 合并一次（debounce），同一活动在窗口内的 N 次提交只重算 1 次。

```java
@Component
public class SubmissionScreenUpdater {
  private final Map<UUID, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(2, r -> { Thread t = new Thread(r, "screen-refresh"); t.setDaemon(true); return t; });

  @TransactionalEventListener
  public void submitted(Submitted event) {
    schedule(event.activityId(), event.questionId());
  }

  private void schedule(UUID activityId, UUID questionId) {
    pending.compute(activityId, (id, future) -> {
      if (future != null) future.cancel(false);                 // 合并：覆盖上一次
      return scheduler.schedule(() -> {
        pending.remove(id);
        try { transaction.executeWithoutResult(s -> refreshLiveData(activityId, questionId)); }
        catch (Exception e) { LOG.warn("screen refresh failed", e); }
      }, 300, TimeUnit.MILLISECONDS);
    });
  }
}
```

2. **`refreshQuestionSubmissionCount` 走增量**：它只需要一个计数，不需要重算 scoreboard。把"提交计数"和"scoreboard 重算"拆成两条独立路径，计数可以 300ms 合并、scoreboard 可以 1s 合并。

3. **去掉冗余广播**：设备同时在活动 topic 和设备 topic 上会导致重复 payload。给设备 payload 只发设备 topic，活动 topic 只发轻量的 `{type, deviceId}` 通知。

---

## P0-3 `screenResponses` 的 N+1

**证据** `ActivityService.java:889-905`：

```java
return submissions
    .findByActivityIdAndQuestionIdOrderBySubmittedAtAsc(activityId, questionId).stream()
    .map(item -> {
      Map<String, Object> response = new HashMap<>();
      Participant participant = participants.findById(item.getParticipantId()).orElse(null);  // ← 循环内查询
```

50 条提交 = 50 次 `SELECT * FROM participants WHERE id=?`。而这段代码每次作答都会跑。

**修复**

```java
List<AnswerSubmission> answered =
    submissions.findByActivityIdAndQuestionIdOrderBySubmittedAtAsc(activityId, questionId);
Set<UUID> participantIds = answered.stream().map(AnswerSubmission::getParticipantId).collect(Collectors.toSet());
Map<UUID, Participant> byId = participants.findAllById(participantIds).stream()
    .collect(Collectors.toMap(Participant::getId, p -> p));     // 1 次查询取代 N 次
return answered.stream().map(item -> {
  Participant participant = byId.get(item.getParticipantId());
  ...
}).toList();
```

同样的 N+1 也在 `ActivityService.buzzes():1830-1834`（`map(entry -> toBuzz(entry, requireParticipant(...)))`），而 `requireParticipant` 内部还会再查一次 `activities`（`participantScopeActivity:1141`），即**每条记录 2 次查询**。

---

## P0-4 连接池 / 线程池 / JVM 全是默认值

**证据**：

- `server/src/main/resources/application.yml` 和 `application-compose.yml` 里 **没有** `spring.datasource.hikari.*`、没有 `server.tomcat.threads.*`、没有 `spring.jpa.properties.hibernate.jdbc.batch_size`。
- `deploy/run-api.sh:26`：`exec java -jar ...`，**无任何 JVM 参数**。

默认值推算：

| 项 | 默认 | 4c8g 建议 |
|----|------|-----------|
| Hikari `maximum-pool-size` | 10 | **20** |
| Hikari `minimum-idle` | 10 | 10 |
| Tomcat `max-threads` | 200 | **100**（50 并发 + 广播余量） |
| Tomcat `accept-count` | 100 | 200 |
| JVM 堆 | 物理内存 1/4（不可控） | **`-Xmx2g -Xms2g`** |
| GC | G1 自适应 | `-XX:+UseG1GC -XX:MaxGCPauseMillis=200` |

**为什么 Hikari=10 是硬顶**：P0-1 制造的 300 个并发请求，每个要 5~10 次 SQL，全部排队等在 10 个连接上。Tomcat 有 200 个线程，但只有 10 个能真正碰数据库，其余 190 个线程在等连接。这是"看起来 CPU 不高但响应很慢"的典型症状。

**修复** `server/src/main/resources/application-compose.yml`：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:20}
      minimum-idle: 10
      connection-timeout: 5000
      validation-timeout: 2000
      max-lifetime: 1200000
      keepalive-time: 300000
  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 30
          fetch_size: 50
        order_inserts: true
        order_updates: true

server:
  tomcat:
    threads:
      max: ${TOMCAT_MAX_THREADS:100}
      min-spare: 20
    accept-count: 200
    max-connections: 2000
```

`deploy/run-api.sh:26`：

```bash
exec /usr/lib/jvm/java-21-openjdk-amd64/bin/java \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/challenging-knowledge-match/logs \
  -Dfile.encoding=UTF-8 \
  -jar "$APP_DIR/server/target/knowledge-match-api-0.1.0.jar" \
  --spring.profiles.active=compose \
  --server.address=127.0.0.1 \
  --server.port="$APP_BACKEND_PORT"
```

> 8GB 分配建议：JVM 2G + PostgreSQL 2.5G + Redis 256M + MinIO 512M + OpenResty 256M + 系统 1G ≈ 6.5G，留 1.5G 余量。

---

## P1-1 每个请求的鉴权开销：5~6 次多余 SQL

一次普通的参与者 `GET /api/activities/{id}/scoreboard` 的鉴权链路：

| 步骤 | 位置 | 查询 |
|------|------|------|
| 1 | `JwtAuthenticationFilter.java:47` → `revocations.isAccessTokenRevoked` | `revoked_access_tokens` EXISTS |
| 2 | 同上，`familyId` 分支（`TokenRevocationService.java:25`） | 再来一次 EXISTS |
| 3 | `ActivityAuthorizationManager.java:40` | `activities.findById` |
| 4 | `:60`（`allowsParticipant` 第一条，**无条件执行**） | `activities.findById` |
| 5 | `:61-63`（仅当活动 ≠ token 的活动，共享参与者场景） | `activities.findById` |
| 6 | `:64-66`（同上） | `activities.findById` |
| 7 | 业务层 `requireActivity` | `activities.findById` |

前 6 步都在事务外（`open-in-view: false`），**Hibernate 一级缓存不生效**，每次都是真查询。

实际命中数：参与者访问**自己的**活动 = 5 次（第 5、6 行被 `activityId.equals(principal.activityId())` 短路）；访问**共享参与者的子活动** = 7 次。

而这些查询拿的是**同一行 `activities` 数据** —— 第 3、4 行完全可以合并，第 5、6 行可以复用第 3 行的结果。

**修复**：加 Caffeine 缓存。

```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

```java
@Component
public class ActivityCache {
  private final Cache<UUID, Optional<Activity>> cache = Caffeine.newBuilder()
      .maximumSize(500).expireAfterWrite(Duration.ofSeconds(3)).build();

  public Optional<Activity> find(ActivityRepository repo, UUID id) {
    return cache.get(id, key -> repo.findById(key));
  }
  public void evict(UUID id) { cache.invalidate(id); }
}
```

- `ActivityAuthorizationManager`：用 `ActivityCache`；`allowsParticipant` 里把 4 次 `findById` 收敛成 1 次（同一个 `Activity` 对象已经拿到了，判断 `code` 完全可以用它）。
- `TokenRevocationService.isAccessTokenRevoked`：吊销是低频写、检查是高频读 → 用 Caffeine 存"未吊销"的 tokenId，3~5 秒 TTL。或者直接查 Redis（项目已有 Redis）。

**这一项能砍掉每个请求 80% 的 SQL 开销**，是投入产出比最高的一条。

---

## P1-2 数据库索引缺失（随历史数据增长恶化）

`V1__initial_schema.sql` 里 `participants` / `questions` / `answer_submissions` / `prize_awards` 都没有 `activity_id` 单列索引。现有可用索引：

| 表 | 现有索引 | 缺失 |
|----|---------|------|
| `answer_submissions` | `uk(activity_id, idempotency_key)`、`uk(idempotency_key)` | `(activity_id, question_id)`、`(activity_id, participant_id)` |
| `participants` | `uk(activity_id, venue, contact)` | 可用最左前缀，勉强够 |
| `questions` | `uk(activity_id, source_question_id)` | `(activity_id)` 单列，`(activity_id, display_order)` |
| `prize_awards` | 无 | `(activity_id)`、`(activity_id, participant_id)` |
| `lottery_draws` | 无 | `(activity_id)` |

`countByActivityIdAndQuestionId`（每次作答调用 2 次：「主事务 1 次 + afterCommit 1 次」）只能靠 `(activity_id, idempotency_key)` 过滤到活动级再扫。

当前数据量（一题 50 行）下这不致命，但**库是跨活动长期累积的** —— 跑过 10 场活动后 `answer_submissions` 就是几万行，届时每次作答的 COUNT 都会退化成活动级扫描。

**修复** 新增 `V28__performance_indexes.sql`：

```sql
create index if not exists idx_answer_submissions_activity_question
  on answer_submissions(activity_id, question_id);
create index if not exists idx_answer_submissions_activity_participant
  on answer_submissions(activity_id, participant_id);
create index if not exists idx_questions_activity_display_order
  on questions(activity_id, display_order);
create index if not exists idx_prize_awards_activity_participant
  on prize_awards(activity_id, participant_id);
create index if not exists idx_lottery_draws_activity
  on lottery_draws(activity_id);
```

> `buzz_entries` 不需要新建索引：`uq_buzz_participant (activity_id, question_id, participant_id)`
> 的最左前缀已经覆盖 `(activity_id, question_id)` 查询。

---

## P1-3 nginx：无 upstream keepalive、无缓存头、JSON 未压缩

> **实测修正**：上机核对后，生产 OpenResty 的**全局 `nginx.conf` 已开启 gzip**（level 2，含 js/css types），
> 静态资源本来就是压缩传输的。最初的判断只看仓库里的 `nginx.conf`，对生产不成立。
> 真正的缺口是下面三条，比"完全没 gzip"要窄，但都成立。

**证据**（生产 `1Panel-openresty-0IEB` 容器 + `/opt/1panel/www/conf.d/match.soyorin.love.conf`）：

1. **`gzip_types` 缺 `application/json`** —— 全局 `nginx.conf:39` 只列了 `text/plain application/javascript ... text/css application/xml`。
   静态 JS/CSS 压了，但**所有 API 的 JSON 响应都没压**。实测 `curl -H 'Accept-Encoding: gzip' /api/health` 无 `content-encoding`。
2. **没有 `upstream ... keepalive`** —— `proxy_pass http://127.0.0.1:8082;` 每个 `/api` 请求新建一条到后端的 TCP 连接。实测优化前 `ss` 里 8082 端口大量 `TIME-WAIT`、0 条 ESTABLISHED 复用。
3. **没有缓存头** —— `index-*.js` 是内容哈希文件名，但响应只有 `ETag`/`Last-Modified`，没有 `Cache-Control`，浏览器每次都要协商。

**静态资源 gzip 效果**（对现有 `dist` 实测，仅作参考，生产已在压缩）：

| 文件 | 原始 | gzip 后 | 压缩比 |
|------|------|---------|--------|
| `index-SZJKapi0.js` | 512,967 B | 151,439 B | **3.4×** |
| `index-Doi2M-R_.css` | 146,634 B | 29,642 B | **4.9×** |
| 合计 | 659,601 B | 181,081 B | **3.6×** |

**修复** `deploy/match.sh-1.soyorin.love.conf`：

两处需要注意的 nginx 语法约束（我第一次改的时候踩了）：

- `gzip on;` **不能**写在站点 conf 里 —— 站点 conf 被 include 进 `http{}`，与全局 `gzip on` 同层，会报 `"gzip" directive is duplicate`。
  但 `gzip_types` / `gzip_proxied` / `gzip_comp_level` **可以在 `server{}` 内覆盖**（它们在 srv 层也合法），不冲突。
- 不要用 `add_header Cache-Control` —— `add_header` 会**清掉** server 层继承的 HSTS。用 `expires` 就够了。

```nginx
# 文件顶部（http 上下文，若无法改全局可放这里）
upstream knowledge_match_api {
    server 127.0.0.1:8082;
    keepalive 64;
}

gzip on;
gzip_comp_level 5;
gzip_min_length 1024;
gzip_vary on;
gzip_proxied any;
gzip_types text/plain text/css application/javascript application/json
           application/xml image/svg+xml text/xml;

server {
    # ...

    location /api/ {
        proxy_pass http://knowledge_match_api;
        proxy_http_version 1.1;
        proxy_set_header Connection "";        # ← 复用连接的关键
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header Forwarded "";
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 哈希命名的静态资源：一年不可变
    location ~* \.(js|css|woff2?|png|jpg|jpeg|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # index.html 永不缓存，保证发版即时生效
    location = /index.html {
        add_header Cache-Control "no-cache";
    }
}
```

同理，根目录 `nginx.conf`（容器部署用）也要补 `gzip` + `upstream keepalive`。

---

## P2 其它可优化点

### P2-1 `responseRank` 在并发下有竞态

`ActivityService.java:640`：

```java
int responseRank = Math.toIntExact(submissions.countByActivityIdAndQuestionId(activityId, question.getId()) + 1);
```

50 人同时提交时，两个请求读到同样的 count → 同样的 rank。`answer_submissions` 上没有 rank 唯一约束，所以不会报错，但**大屏"第 N 位答对"会显示重号**。

`buzz()` 的 `:1823` 同样的问题，但 `buzz_entries` 有 `uq_buzz_rank` 约束，所以并发下会抛 **500 而不是友好提示**（`ApiExceptionHandler` 若未单独处理 `DataIntegrityViolationException`）。

修复：`responseRank` 用数据库序列/窗口函数，或接受"近似"并在大屏上不展示名次；`buzz` 捕获 `DataIntegrityViolationException` 转成 `409 已有人抢先`。

### P2-2 抢答模式下 Activity 行锁持锁时间过长

`ActivityService.java:1809-1826`：

```java
Activity activity = activities.findForUpdate(activityId)...;   // ← 悲观写锁，全局单行
...
ControlState state = controlState(activityId);
if (orderedQuestionsForActivity(activityId).stream().noneMatch(...))  // 全量加载题库 + 条目
...
int rank = count(...) + 1;
buzzEntries.save(...);
broadcast(activityId, "buzz.received");                        // 锁内做 I/O
```

抢答是"越多人同时按越慢"的场景（50 人同时点），而这里在**持有 Activity 单行写锁期间**做了 8~10 次查询（含全量题库加载）和一次广播。50 个并发抢答会排队，尾延迟很高，甚至锁等待超时。

修复：把只读校验（`controlState`、`orderedQuestionsForActivity`）全部移到加锁之前，锁内只保留「查重 → rank → insert」。

```java
// 先做无锁校验
ControlState state = controlState(activityId);
List<Question> open = orderedQuestionsForActivity(activityId);
// 再加锁，临界区只做最少的写
Activity activity = activities.findForUpdate(activityId).orElseThrow(...);
ensureAnsweringAllowed(activity);
...
int rank = count(...) + 1;
buzzEntries.save(...);
// broadcast 移到事务提交后（afterCommit）
```

### P2-3 `broadcast()` 每条消息多一次查询 + 一次列表查询

`ActivityService.java:1743-1757`：

```java
private void broadcast(UUID activityId, String type) {
  realtime.send(...);
  Activity source = activities.findById(activityId).orElse(null);   // ← 每次广播一次查询
  ...
  for (Activity child : activities.findByParentActivityId(root)) {  // ← 再查一次
```

一次作答会走 `broadcast` 1~2 次。可以通过函数传入已有的 `Activity` 对象省掉 `findById`；`findByParentActivityId` 结果可短 TTL 缓存。

### P2-4 `SiteSettingsService.current()` 每次查库，无缓存

`SiteSettingsService.java:65-67`，`findById(1L)`。`/api/site` 是每个客户端每次 `load()` 都会调用的接口（`App.jsx:5941,5970`）。配置读多写极少，加个 Caffeine/`@Cacheable` 即可，写时 evict。

### P2-5 `listActivities()` 全表加载

`ActivityService.java:136`：`activities.findAll()`。控制台首屏会拉全部历史活动。当前量小，但跨场次累积后建议加 `pageable` 或按 `status` 过滤。

### P2-6 WebSocket 出站线程池未配置

`WebSocketConfig.java` 只覆盖了 `configureClientOutboundChannel` 的 interceptor（`:34-36`），没有设置 Executor。Spring 默认按 CPU 数量派生少量线程，而这个应用会把**完整 scoreboard** 作为 payload 扇出给所有订阅者 —— 4 核上 JSON 序列化 + 写 50 个 socket 会成为瓶颈。

修复：

```java
@Override
public void configureClientOutboundChannel(ChannelRegistration registration) {
  registration.interceptors(jwtChannelInterceptor.outboundAuthorization());
  registration.taskExecutor()
      .corePoolSize(8).maxPoolSize(16)
      .queueCapacity(1000)
      .keepAliveSeconds(60);
}
```

同时（与 P0-2 配套）**不要推送全量 scoreboard**，只推 `{type, questionId, submittedCount}`，客户端在需要时再拉。

### P2-7 前端包体与静态资源

- `App.jsx` 单文件 **7743 行 / 306 KB**，打包成单个 500 KB chunk → 可以 `React.lazy` 拆分控制台/大屏/参与者三条路由，首屏只需加载当前角色的代码。
- `public/knowledge-match-user-manual.pdf` **17.2 MB** 被打进 `dist` 和镜像。它不参与首屏，但会显著增大镜像和静态目录。建议移出 `public/`，作为独立分发物或对象存储里的链接。

### P2-8 Redis 每次广播同步 RTT

`RealtimeEventBus.java:51`：`redis.convertAndSend(...)` 是同步调用，在请求线程里等 Redis 往返。`REALTIME_REDIS_ENABLED=true`（`run-api.sh:16`）时，每次作答有 6+ 次 Redis RTT。单节点部署其实不需要 Redis 转发 —— 可以用配置开关按单机/多机切换（`REALTIME_REDIS_ENABLED=false` 时 `RealtimeEventBus:46-48` 直接走本地 STOMP）。**如果只跑一台机器，把它关掉是最省事的一条优化。**

---

## 三、建议的执行顺序

按「改动小 / 收益大」排序：

1. **配置层（30 分钟，零代码风险）**
   - `application-compose.yml` 加 Hikari + Tomcat + batch_size
   - `run-api.sh` 加 `-Xms2g -Xmx2g`
   - `match.sh-1.soyorin.love.conf` 加 gzip + upstream keepalive + Cache-Control
   - 单机部署时 `REALTIME_REDIS_ENABLED=false`
   - 新增 `V28__performance_indexes.sql`

2. **鉴权缓存（半天，收益最大）**
   - `ActivityCache` + `ActivityAuthorizationManager` 收敛 `findById`
   - `TokenRevocationService` 加负缓存
   - `SiteSettingsService` 加缓存
   → 每个请求省掉 5~6 次 SQL

3. **消除 N+1（半天）**
   - `screenResponses` 批量 `findAllById`
   - `buzzes` 批量

4. **削峰（1~2 天，动前端和后端）**
   - 大屏刷新改 `@Async` + 300ms 合并
   - 广播 payload 从全量改成增量
   - 前端 `load()` 拆成静态/动态，按事件类型分流
   → 把 300 请求 + 3000 查询的波峰压到 1/5 以下

5. **收尾**
   - 抢答临界区缩小
   - `responseRank` 竞态
   - 前端路由懒加载、PDF 移出 `public`

---

## 四、验证方式

改完后用下面的方式量化，不要只看"感觉快了"：

```bash
# 1. 压测单接口（50 并发，就是目标值）
hey -n 3000 -c 50 -H "Authorization: Bearer $TOKEN" \
  https://match.soyorin.love/api/activities/$ACTIVITY_ID/scoreboard

# 2. 看数据库连接占用
psql -c "select count(*), state from pg_stat_activity where datname='knowledge_match' group by state;"

# 3. 看慢查询
psql -c "select query, calls, mean_exec_time, total_exec_time
         from pg_stat_statements order by total_exec_time desc limit 20;"

# 4. 看 JVM
jcmd $(pgrep -f knowledge-match-api) GC.heap_info

# 5. 看 nginx 上游连接复用是否生效
ss -tan '( dport = :8082 )' | awk 'NR>1{print $1}' | sort | uniq -c
```

关键验收指标（4c8g）：

| 指标 | 目标 |
|------|------|
| `/scoreboard` p95 | < 80ms |
| 作答提交 p95 | < 150ms |
| PostgreSQL 活跃连接 | < 20 |
| JVM 老年代占用 | < 1.5G |
| 单题作答波峰 QPS（后端） | < 400 |

`deploy/README.md:57` 已经写明「Compose parsing and local H2 tests alone do not establish production connectivity or concurrency capacity」—— 这份审查补的就是这一块，但**最终仍要在真机上按上面第 1 条压一遍**。

---

## 五、已实施（2026-10-18，生产）

本次落地了上面全部四步：配置层、鉴权缓存、N+1 消除、以及第 4 步的削峰（见 5.4）。

### 5.1 代码改动

| 文件 | 改动 |
|------|------|
| `server/pom.xml` | 新增 Caffeine 依赖 |
| `security/ActivityAccessCache.java` | **新增**：Caffeine 缓存活动授权相关字段的不可变快照（2s TTL，`maximumSize 2000`） |
| `security/ActivityAuthorizationManager.java` | 4 次 `activities.findById` → 1 次缓存读 |
| `security/ActivityScopes.java` | 抽出 `resolve()`，与缓存共用同一条 venue 作用域规则 |
| `security/StompJwtChannelInterceptor.java` | 订阅鉴权同样改走缓存 |
| `security/TokenRevocationService.java` | 已吊销 token 查询加 3s 负缓存 |
| `service/SiteSettingsService.java` | 公开站点配置缓存 60s，写时失效 |
| `service/ActivityService.java` | `screenResponses` / `buzzes` 的 N+1 改为批量 `findAllById` |
| `resources/application-compose.yml` | Hikari 20、Tomcat 100、JPA batch/fetch size、缓存 TTL |
| `db/migration/V28__performance_indexes.sql` | **新增**：5 个索引 |
| `deploy/run-api.sh` | `-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError` |
| `deploy/match.sh-1.soyorin.love.conf` | upstream keepalive、JSON gzip、静态缓存头 |
| `nginx.conf` | 同步（容器部署形态） |

**测试隔离**：`app.cache.*-ttl-seconds=0` 写进 `server/src/test/resources/application.properties`，
缓存窗口在测试里整体 bypass，避免"改完立刻断言"被 TTL 挂住。125 个测试全绿。

### 5.2 PostgreSQL 调优（零停机）

用 `ALTER SYSTEM` + `pg_reload_conf()` 完成，**未重启**，共存的 new-api / school_exam / hr_system / ztnet / soyorin 全程无断连（49 条连接保持不变）。

数据依据：全库总计仅 110MB，`knowledge_match` 11MB —— **工作集本来就在 `shared_buffers`(128MB) 和页缓存里**，所以调 `shared_buffers` 收益≈0，反而要重启，故**不动**。

| 参数 | 原值 | 新值 | 理由 |
|------|------|------|------|
| `random_page_cost` | 4 | **1.5** | 云盘 SSD，降低后规划器更愿意走索引 |
| `effective_io_concurrency` | 16 | **64** | SSD 并发读 |
| `work_mem` | 4MB | **8MB** | 减少落盘排序；×100 连接仍可控 |
| `maintenance_work_mem` | 64MB | **256MB** | 更快 vacuum / 建索引 |
| `wal_compression` | off | **on** | 降 WAL 写量 |
| `track_io_timing` | off | **on** | 观测 |
| `jit` | on | **off** | OLTP 短查询不需要 JIT |
| `effective_cache_size` | 4GB | **3GB** | 7.1GB 共享机上更贴实 |

未做：`pg_stat_statements`（需 `shared_preload_libraries`，必须重启 → 影响 6 个共存应用，留待用户决定）。

### 5.3 实测结果

部署后在生产机上压测（同一台 4 核机，客户端与服务端共享 CPU，**数值偏保守**）。

单请求基线（直连 API，串行）：**p50 = 14.4ms**

50 并发、连接复用（浏览器真实行为），端点返回 12.6KB JSON：

| 场景 | 请求数 | 失败 | p50 | p95 | p99 | max |
|------|--------|------|-----|-----|-----|-----|
| 直连 API（无 TLS） | 400 | 0 | 36.9ms | 185.8ms | 235.4ms | 285.8ms |
| 经 nginx+TLS，连接复用 | **2000** | **0** | — | — | **65.5ms** | 111.3ms |
| 经 nginx+TLS，每请求新建 TLS | 400 | 0 | 361ms | 490ms | 543ms | 555ms |

**关键结论**：第 3 行不是应用慢，而是**每请求一次 TLS 握手**的代价 —— 用不同并发模型重测后，
连接复用的真实浏览器场景是 **2000 请求、0 失败、p99 65ms**。
gzip 开/关同路径对比 p99 `42.1ms` vs `38.3ms`（噪声范围内），说明 level 4 压缩 12.6KB JSON 的成本可忽略。

运行态确认：

- `HikariPool knowledge-match-pool` 建立，连接数稳定在 21（pool max 20 + 管理连接）
- 堆：`garbage-first heap total 2097152K`（= 2GB，`-Xmx2g` 生效），压测峰值 used 665MB
- 应用 `ERROR` 日志：**0 条**（重启窗口内 `/ws` 的 `connection refused` 是预期，客户端 2.5s 重连）
- `deploy/smoke-production.sh` 与 `deploy/verify-sh-1.soyorin.love.sh` 全绿

### 5.4 第 4 步：削峰（2026-10-18 晚，已实施）

这一轮专门解决 P0-1 / P0-2 的放大因子。核心思路不是"让单个请求更快"，而是**减少事件数量**。

#### 服务端：把 50 条广播压成 1 条

| 文件 | 改动 |
|------|------|
| `realtime/ScoreboardChangeBroadcaster.java` | **新增**：按活动合并计分类广播，每个窗口最多一条 `score.updated` |
| `service/SubmissionScreenUpdater.java` | 大屏刷新改为按活动**尾部防抖**（默认 300ms），合并期间只重算一次 |
| `service/ActivityService.java` | `broadcast()` 分流：计分类事件走合并器，其余（`control.updated` 等）保持**立即**发送 |

`control.updated` 必须立即发 —— 主持人开题要是延迟，所有人都会看到旧题目。这条边界有测试守着。

窗口可配：`SCORE_BROADCAST_WINDOW_MS`（默认 250）、`SCREEN_REFRESH_WINDOW_MS`（默认 300）。
设为 0 则退回旧的同步行为 —— 测试环境就是这么关掉的，保证断言确定性。

#### 前端：6 个请求 → 4 个，静态数据只拉一次

| 位置 | 改动 |
|------|------|
| `src/App.jsx` `useActivityStream` | 支持传入 id 数组；把窗口内的事件类型收集成 `Set` 交给回调；防抖 200→300ms |
| 参与者页 `load()` | 拆成 `loadBrand`（活动+站点配置，仅挂载/`activity.updated` 时拉）、`loadRegistration`、`loadLive`（4 个实时接口） |
| 参与者页订阅 | 原本两条 `useActivityStream`（会场 + 身份活动）→ 合并成一条 socket |
| `onStreamEvent` | 用 `useRef` 固定回调身份，避免 loader 身份变化导致 socket 反复重建 |

> 10 秒保险轮询保留，且传 `undefined` 表示"全量刷新"。
> 这既是兜底（万一某条通知被合并掉），也是拆分静态/实时数据的安全网。

#### 验证：126 个后端测试 + 70 个前端测试全绿

新增 `ScoreboardCoalescingIntegrationTest` 直接量化放大倍数：

```
[coalescing] 20 answers produced 1 score notifications
```

**20 次作答 → 1 条广播（原来是 20 条）。** 同一测试还断言 `control.updated` 仍然立即送达。

按 50 人同题计算：

| | 改前 | 改后 |
|---|---|---|
| 广播条数 | 50 | ~1~2 |
| 每客户端每次请求数 | 6 | 4 |
| **单题总请求量** | **50×50×6 = 15000** | **50×2×4 = 400** |

约 **37 倍**的削峰。作答 HTTP 响应也不再等大屏重算（重算已移出请求线程）。

#### 压测：无回归

同一台机、50 并发、连接复用，连测 3 轮（每轮 2000 请求）：

| 轮次 | p50 | p90 | p95 | p99 | max |
|------|-----|-----|-----|-----|-----|
| run1 | <1ms | <1ms | <1ms | 69.4ms | 228.6ms |
| run2 | <1ms | <1ms | <1ms | 63.4ms | 165.5ms |
| run3 | <1ms | <1ms | <1ms | 45.6ms | 192.3ms |

与改造前（p99 65.5ms）一致，应用 `ERROR` 0 条。读取路径本来就没动，这里是确认没被改坏。

### 5.5 遗留风险（需要你们决策）

1. ~~磁盘 95% 满~~ → **已清理，现 82%（5.1G 可用）**。删掉了 13 份过期回滚包、11 个 `/opt/update-*`
   旧构建目录、未引用的 `/opt/jdk21`（346M），并把 journal 从 1G 收到 300M。保留了最近 2 份回滚包。
2. **无 swap** —— 7.1G 内存无交换区，任何进程失控都是 OOM kill。已用 `ExitOnOutOfMemoryError` + systemd 自动拉起兜底。
3. **Flyway 报 PostgreSQL 18.4 超出支持范围**（最新支持 17）—— 本次迁移正常执行，但升级 Flyway 值得排期。
4. **P2 项仍未做** —— `responseRank` 并发竞态、抢答临界区过大、前端路由懒加载（500KB 单 chunk）。
   这些不影响 50 并发能不能跑，属于下一轮。
5. **合并窗口是有意的延迟** —— 计分类通知最多晚 250ms、大屏最多晚 300ms。若某场活动要求"零延迟"
   视觉反馈，把 `SCORE_BROADCAST_WINDOW_MS` / `SCREEN_REFRESH_WINDOW_MS` 调小即可，代价是放大倍数回升。

---

## 附：本次审查已确认「不是问题」的部分

避免重复劳动，以下几处看起来可疑但实测设计合理：

- `open-in-view: false`（`application.yml:16`）已关闭，没有 OSIV 长事务。
- `score_ledgers` 有 `idx(activity_id, participant_id)`（`V2:74`），`activityScores` 的 GROUP BY 走索引。
- `participant.addScore` 走 `findByIdForUpdate`，分数不会丢更新。
- Flyway 迁移 + `ddl-auto: validate`，schema 不会漂移。
- `/ws` 的 `proxy_read_timeout 3600s` 和 `Upgrade` 头都正确，WebSocket 不会被 nginx 提前掐断。
- 媒体走 `proxy_buffering off` + byte-range，大文件不会占 API 内存。
- `buzz_entries` 的最左前缀已覆盖题目级查询，无需新索引。
