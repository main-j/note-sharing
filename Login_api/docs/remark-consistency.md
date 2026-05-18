# Remark 模块一致性设计

> 适用范围：评论点赞 / 取消点赞链路（`RemarkService.likeRemark` / `cancelLikeRemark` / `flushLikeCountToMQ` / `flushLikeUsersToMQ` 及 `RemarkConsumer`）
>
> 目标：在高并发点赞场景下，用 **Redis 作热缓存 + 写聚合层，MongoDB 作权威持久化** 的方式，做到"读取低延迟、写入最终一致，且缓存过期不会丢数据"。
>
> 整体设计参照 `NoteStatsService` / `NoteStatsConsumer`，**但不引入补偿表机制**——消费者最终失败仅记日志，由下一次定时 flush 自然重投递。

---

## 1. 架构总览

```text
┌─────────────────┐    like / cancel    ┌──────────────────────────┐
│  Controller /   │ ─────────────────▶  │      RemarkService       │
│  WebSocket      │                     │   (Redis 是热缓存 +       │
└─────────────────┘                     │    写聚合层，非权威源)    │
                                        └──────────┬───────────────┘
                                                   │
                                ① 写路径 read-through(initStatsIfNeeded)
                                                   │
                                                   ▼
                                  ┌────────────────────────────────┐
                                  │   Redis (TTL 7 天)             │
                                  │   ─ remark_stats:{id} Hash    │
                                  │       count / version /       │
                                  │       last_activity_at        │
                                  │   ─ remark_user_like:{id} Set │
                                  └────────────────┬───────────────┘
                                                   │
                                ② 定时 flush（每 5 分钟）
                                                   ▼
                                  ┌────────────────────────────────┐
                                  │ RabbitMQ                       │
                                  │  ─ remarkLikeCount.redis.queue│
                                  │  ─ remarkLikeUsers.redis.queue│
                                  └────────────────┬───────────────┘
                                                   │
                                ③ RemarkConsumer 落盘 + deleteIfCold
                                                   ▼
                                  ┌────────────────────────────────┐
                                  │ MongoDB (权威持久化)           │
                                  │  ─ remark_count (+@Version)    │
                                  │  ─ remark_likes (+@Version)    │
                                  └────────────────────────────────┘
```

**核心机制清单：**

| 关注点 | 设计选择 |
|---|---|
| 谁是权威源 | **MongoDB**（重要语义变化）。Redis 仅作热缓存 + 短期写聚合，缓存缺失要从 MongoDB 回填 |
| 缓存缺失如何处理 | `initStatsIfNeeded` 在 like/cancel/读 之前 read-through 加载，**避免计数被清零腰斩** |
| 何时落盘 | 定时任务每 **5 分钟** 把 Redis 状态投递到 RabbitMQ，消费者写入 MongoDB |
| 写并发冲突 | 用 `@Version` 字段做乐观锁，冲突时回退到 "delta 重算 + 重试 3 次" |
| 落盘成功后 | `deleteIfCold` 删除冷 Redis key，让下次访问 read-through 重新加载新 version |
| TTL | **7 天** ≫ flush 周期 5 分钟，保证任意时刻 Redis 都能撑到下一次 flush |

---

## 2. Redis Key 设计

| Key 模板 | 类型 | 字段 / 含义 | TTL |
|---|---|---|---|
| `remark_stats:{remarkId}` | **Hash** | `count` (long) / `version` (long) / `last_activity_at` (ISO timestamp) | 7 天 |
| `remark_user_like:{remarkId}` | Set\<Long\> | 点赞过该评论的用户 ID 集合（Long） | 7 天 |

> ⚠️ 已废弃：旧的 `remark_like_count:{id}` 与 `remark_like_version:{id}` 已合并到 `remark_stats:{id}` Hash。
>
> 用户 ID 统一以 Long 形式存储；序列化层（`Jackson2JsonRedisSerializer` + `NON_FINAL`）对 Long 写裸数字。

---

## 3. 写路径

### 3.1 `initStatsIfNeeded` —— read-through 的关键修复

```text
1. 若 Redis remark_stats:{id} Hash 为空 → 从 MongoDB 读 RemarkCountDO
     写入 Hash 的 count / version / last_activity_at
2. 若 Redis remark_user_like:{id} Set 为空 → 从 MongoDB 读 RemarkLikeByUsersDO
     SADD 全部 userList 进 Set
3. 双双刷新 TTL 到 7 天
```

**为什么这个步骤至关重要？**

旧设计在缓存缺失时把 count 直接 SET 为 0 然后 +1，导致一个典型 bug：

> 某热门评论 MongoDB 已有 5000 个赞，Redis TTL 过期。第 5001 个用户来点赞：
> - 旧设计：count = 0 → +1 = **1**，flush 写 MongoDB → **腰斩 4999 个赞**
> - 新设计：`initStatsIfNeeded` 先把 5000 加载进 Redis → +1 = **5001** ✅

集成测试 `likeRemark_redisMissAndDbHasExistingCount_readsThroughInsteadOfResetting` 专门覆盖这个场景。

### 3.2 `likeRemark(remarkId, userId)`

```text
1. initStatsIfNeeded(remarkId)        ← read-through 兜底
2. 若 SISMEMBER user_set, userId      → 返回 false（已点赞）
3. SADD userId
4. HINCRBY remark_stats:{id} count 1  （负值 → HSET 为 0）
5. HSET remark_stats:{id} last_activity_at = now
6. 刷新两个 key 的 TTL → 7 天
7. 返回 true
```

### 3.3 `cancelLikeRemark(remarkId, userId)`

```text
1. initStatsIfNeeded(remarkId)        ← read-through 兜底
2. 若 SISMEMBER user_set, userId == false → 返回 false（未点赞过）
3. SREM userId
4. HINCRBY remark_stats:{id} count -1  （负值 → HSET 为 0）
5. HSET remark_stats:{id} last_activity_at = now
6. 刷新两个 key 的 TTL → 7 天
7. 返回 true
```

### 3.4 重要语义变化：version 不再由写路径递增

| 维度 | 旧设计 | 新设计 |
|---|---|---|
| version 谁来维护 | Redis 中由 like/cancel 每次 +1 | **MongoDB `@Version` 自动维护**；Redis 中的 version 是 read-through 时从 DB 拷贝的初始值 |
| 何时推进 | 写操作时 | Consumer 成功落盘时 |
| Redis 中是否同步 | 是 | 否——靠 `deleteIfCold` 删 Redis 让下次访问重新加载新 version |

这是和 notestat 完全一致的 version 模型。

---

## 4. flush 路径：把 Redis 状态推送到 MQ

定时任务 `RemarkScheduledTasks`：

```java
@Scheduled(cron = "0 */5 * * * *")  // 每 5 分钟，远小于 7 天 TTL
public void flushRedisLikeCountToMQ() { remarkService.flushLikeCountToMQ(); }

@Scheduled(cron = "0 */5 * * * *")
public void flushRedisLikeUsersToMQ() { remarkService.flushLikeUsersToMQ(); }
```

### 4.1 `flushLikeCountToMQ`

- 扫描所有 `remark_stats:*` Hash
- `HGETALL` 取 count / version / last_activity_at
- 发送消息到 `remarkLikeCount.redis.queue`

```json
{
  "remarkId": "xxx",
  "likeCount": 5,
  "version": 3,
  "last_activity_at": "2026-05-14T20:00:00"
}
```

### 4.2 `flushLikeUsersToMQ`

- 扫描所有 `remark_user_like:*` Set
- `SMEMBERS` 取所有成员，统一规范化为 `Set<Long>`
- **version 与 last_activity_at 从同一 `remark_stats:{id}` Hash 取**（单一事实源）
- 发送消息到 `remarkLikeUsers.redis.queue`

```json
{
  "remarkId": "xxx",
  "users": [1001, 1002],
  "version": 3,
  "last_activity_at": "2026-05-14T20:00:00"
}
```

---

## 5. 消费路径：`RemarkConsumer`

两个 listener 对称处理 count 和 users，各自走"Layer 1 直接覆盖 → Layer 2 增量/重写 + 重试 3 次"的两层一致性策略。

### 5.1 count 路径

```text
Layer 1  ── version 一致时直接覆盖
  if existing == null OR existing.version == msg.version:
      countDO.likeCount = msg.likeCount
      save()   ← @Version 自动 +1；冲突抛 OptimisticLockingFailureException

Layer 2  ── version 不一致或 Layer 1 冲突 → delta 重算 + 重试
  for attempt in 1..3:
      delta = msg.likeCount - dbLikeCount
      countDO.likeCount = max(0, dbLikeCount + delta)
      save()
      若再冲突 → 重试，最多 3 次

最终 ── 成功一次后调用 deleteStatsIfCold
最终失败 ── 仅 log.error，不写补偿表；等下一次 flush 重发
```

### 5.2 users 路径

集合没有 delta 概念，Layer 2 同样是"重新拉 + 覆盖"，最多 3 次重试。

```text
Layer 1  ── version 一致时直接覆盖
Layer 2  ── version 不一致 → forceOverwriteUsers + 乐观锁重试 3 次
最终 ── 成功调用 deleteUsersIfCold；失败仅日志
```

### 5.3 `deleteIfCold` —— Redis 与 MongoDB 自然对齐的关键

```text
落盘成功后：
  redisLast = HGET remark_stats:{id} last_activity_at
  if redisLast == null OR redisLast ≤ msg.last_activity_at:
      DEL  remark_stats:{id}   (count 路径)
      或   remark_user_like:{id} (users 路径)
  else:
      保留 Redis（有新写入，下一轮 flush 再同步）
```

**含义**：消息处理成功后，若期间 Redis 没有新写入（last_activity_at 未前进），就把对应 key 删掉。下次访问会触发 `initStatsIfNeeded` 从 MongoDB 重新读，**带回新的 `@Version`**。这避免了"Redis 上 version 永远落后 DB"的死循环。

### 5.4 两套 version 的协作

| 概念 | 谁维护 | 用途 |
|---|---|---|
| Redis Hash `version` 字段 | `initStatsIfNeeded` 从 DB 加载 | 给 flush 消息打"我从哪个版本的 DB 派生而来"的标签 |
| MongoDB `@Version` | Spring Data 在 `save()` 时自动 +1 | 防止并发 save 互相覆盖 |

两者协作：MQ 消息里的 `version` 是 Redis 侧的"快照标签"，consumer 拿它与 MongoDB 当前 `@Version` 比对，决定走 Layer 1 还是 Layer 2。

---

## 6. 与 NoteStats 的对照

| 维度 | NoteStats | Remark（本次改造后） |
|---|---|---|
| Redis 结构 | `note_stats:{id}` Hash | `remark_stats:{id}` Hash + `remark_user_like:{id}` Set |
| read-through | `initRedisIfNeeded` | `initStatsIfNeeded`（同名理念） |
| `last_activity_at` | ✅ | ✅ |
| 消费者 Layer 1 + 2 | ✅ | ✅ |
| `deleteIfCold` | ✅ | ✅（Hash 与 Set 各一份） |
| 补偿表（CompensationDO） | ✅ Layer 5 | ❌ **不引入**——失败仅日志 |
| TTL | 7 天 | 7 天 |
| flush 周期 | 30 min（可调） | **5 min** |

去掉补偿表的代价：**极小概率的消息处理永久失败**（消费 3 次都乐观锁冲突 + 下次 flush 周期之前 Redis key 又被 deleteIfCold 删了）。考虑到 TTL = 7 天 ≫ flush = 5 分钟，下一周期会再次推送对账消息，实际风险可忽略。

---

## 7. 失败场景与处理

| 场景 | 处理 |
|---|---|
| Redis 宕机 / key 过期 | read-through 兜底：写路径会从 MongoDB 回填，**不会把已有点赞数清零** |
| MQ 投递失败 | flush 是周期任务，5 min 后自动重试 |
| Consumer 并发处理同一 remarkId | `@Version` 乐观锁拦截，Layer 1 失败回退 Layer 2 重试 |
| Consumer 持续乐观锁失败（>3 次） | 记 error 日志放弃；下一周期 flush 会再投递新消息（无补偿表） |
| flush 拿到空 key 集 | 直接返回，不发任何消息 |
| 缓存与 DB 不一致（计数 < 0） | 检测到立即 `HSET count = "0"` |
| Redis 上 version 长期落后 DB | `deleteIfCold` 在每次成功落盘后清掉冷 key，强迫下次重新加载 |

---

## 8. 测试架构

```
src/test/java/com/project/login/
├── service/remark/
│   ├── RemarkServiceUnitTest.java   ── 10 用例，纯 Mockito，毫秒级
│   └── RemarkServiceIT.java         ── 9 用例，真实 Redis + MongoDB
└── service/rabbitmq/consumer/
    └── RemarkConsumerTest.java      ── 10 用例，覆盖 Layer 1/2 + deleteIfCold
```

**29 个用例全绿。**

### 8.1 `RemarkServiceUnitTest`（单元测试）

- `@ExtendWith(MockitoExtension.class)` + `@InjectMocks`
- 全部 Repository / Template / Mapper / Service 由 `@Mock` 注入
- 显式 mock `HashOperations` 验证 Hash 写入字段
- 覆盖：read-through 加载已有 count、首次点赞 Hash 初始化、负值复位、重复点赞拒绝、flush 消息内容
- 关键断言：`hashOps.put(STATS_KEY, F_COUNT, "42")` —— DB 已有的 42 个赞被 read-through 正确加载

### 8.2 `RemarkServiceIT`（集成测试）

- `@SpringBootTest` + 真实 Redis + 真实 MongoDB
- `@MockitoBean RabbitTemplate` 拦截 MQ 调用
- `@BeforeEach` / `@AfterEach` 同时清理 Redis 与 MongoDB，根治状态污染
- 每用例独立 `UUID.randomUUID()` remarkId
- TTL 区间断言 `isBetween(1L, 7 days)`
- **关键用例 `likeRemark_redisMissAndDbHasExistingCount_readsThroughInsteadOfResetting`**：在 MongoDB 预置 100 个点赞，验证缓存缺失时第 101 次点赞结果是 101 而非 1

### 8.3 `RemarkConsumerTest`（消费者单测）

- 纯 Mockito，新增 `RedisTemplate` 依赖用于 deleteIfCold
- 覆盖：
  - `countCreateNewAndDeletesColdRedis`：新插入文档 + 删冷 Redis
  - `countLayer1_overwriteWhenVersionMatches`：version 匹配走 Layer 1
  - `countLayer2_deltaWhenVersionMismatches`：version 不匹配走 Layer 2
  - `countLayer2_retriesOnOptimisticLockFailure`：乐观锁冲突重试，至少 2 次 save
  - `countSuccess_redisHotterThanMessage_keepsRedis`：Redis 有新写入时不删
  - users 路径对称 4 个用例
  - `skipOnEmptyRemarkId`：防御性测试

---

## 9. 运行测试

```bash
# 全量
./gradlew test

# 只跑 remark 相关
./gradlew test \
    --tests "com.project.login.service.remark.*" \
    --tests "com.project.login.service.rabbitmq.consumer.RemarkConsumerTest"

# 仅单元测试（无外部依赖，最快）
./gradlew test --tests "com.project.login.service.remark.RemarkServiceUnitTest"
./gradlew test --tests "com.project.login.service.rabbitmq.consumer.RemarkConsumerTest"

# 集成测试（需要本地起 Redis / MongoDB / MySQL / RabbitMQ / MinIO）
./gradlew test --tests "com.project.login.service.remark.RemarkServiceIT"
```

| 文件 | 用例数 | 外部依赖 |
|---|---|---|
| `RemarkConsumerTest` | 10 | 无 |
| `RemarkServiceUnitTest` | 10 | 无 |
| `RemarkServiceIT` | 9 | Redis、MongoDB、Spring Context |

---

## 10. 设计权衡与未决议题

### 已采用的权衡

| 决定 | 收益 | 代价 |
|---|---|---|
| MongoDB 为权威源，Redis 作热缓存 | 缓存过期不会丢数据；与 NoteStats 心智模型一致 | 缓存命中率不达预期时 read-through 多一次 DB 读 |
| 5 分钟 flush + 7 天 TTL | 任意时刻 Redis 都能撑到下一次 flush；冷数据自然过期 | MongoDB 数据最大延迟 5 分钟（业务可接受） |
| Layer 1 直接覆盖 + Layer 2 delta 重试 | 大多数情况一次成功；冲突时仍能收敛 | 极端并发可能 3 次重试都失败 |
| 不引入补偿表 | 架构简单、表结构少一张 | 极端失败下需等待下次 flush 重发（5 min 周期） |
| `deleteIfCold` | Redis 与 DB 自然对齐，version 不会长期偏离 | 删除后下次访问多一次 read-through 开销 |

### 可改进点

1. **`KEYS *` → `SCAN`**：当前 flush 仍用 `redisTemplate.keys(...)` 全扫，与 NoteStats 一致但生产 Redis 上是阻塞命令。可改为 `SCAN` + 游标分页。
2. **`userList` 大 key 治理**：热门评论的用户集合可能膨胀到数 MB。可考虑改为反向索引 `user_liked:{userId} → Set<remarkId>`，或哈希分桶。
3. **更短的对账周期**：若业务要求秒级一致，可改为"操作日志式"——每次 like 直接发 +1/-1 MQ 消息，flush 退化为定时对账。
4. **`@Transactional` 实际作用范围**：`likeRemark` / `cancelLikeRemark` 主要写 Redis，Spring 事务无法回滚 Redis。这个注解目前几乎只装饰，可移除以避免误导。
5. **监控指标**：消费者乐观锁冲突率 / flush 消息条数 / `deleteIfCold` 触发率，都值得接入 Micrometer。

---

## 11. 关键文件索引

| 路径 | 作用 |
|---|---|
| `service/remark/RemarkService.java` | 主服务，含 `initStatsIfNeeded` / like / cancel / flush |
| `service/remark/RemarkScheduledTasks.java` | 5 min 定时 flush |
| `service/rabbitmq/consumer/RemarkConsumer.java` | MQ 消费者，含两层一致性 + deleteIfCold |
| `model/dataobject/RemarkCountDO.java` | 点赞数文档（`@Version`） |
| `model/dataobject/RemarkLikeByUsersDO.java` | 用户列表文档（`@Version`） |
| `config/RedisConfig.java` | Redis 序列化配置 |
| `service/notestats/NoteStatsService.java` | 设计参照来源 |
| `service/rabbitmq/consumer/NoteStatsConsumer.java` | 设计参照来源 |
| `test/.../RemarkServiceUnitTest.java` | 单元测试 |
| `test/.../RemarkServiceIT.java` | 集成测试 |
| `test/.../RemarkConsumerTest.java` | 消费者测试 |

---

## 12. 改动日志（相对于初版一致性设计）

| 日期 | 变更 | 动机 |
|---|---|---|
| —— | 旧设计：Redis 多 key（count / version / user_like）+ 15 min TTL + 30 min flush + version 由 like/cancel 维护 | 初始实现 |
| 2026-05-14 | 全面参照 NoteStats 改造：Hash 结构、initStatsIfNeeded read-through、last_activity_at、deleteIfCold、TTL 7 天、flush 5 min；version 改由 MongoDB `@Version` 维护 | 修复"缓存过期 → 计数腰斩"风险；统一与 NoteStats 的心智模型 |
| 2026-05-14 | 消费者保留 Layer 1 + Layer 2 重试，**不引入补偿表** | 用户明确要求 |
