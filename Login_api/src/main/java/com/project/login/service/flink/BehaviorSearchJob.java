
package com.project.login.service.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.model.event.UserBehaviorEvent;
import com.project.login.model.event.UserSearchEvent;
import com.project.login.service.recommend.event.RecommendEventType;
import com.project.login.service.recommend.event.model.RecommendEvent;
import com.project.login.service.recommend.model.ItemType;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.configuration.DeploymentOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BehaviorSearchJob {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String REDIS_HOST = System.getenv().getOrDefault("RECOMMEND_REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("RECOMMEND_REDIS_PORT", "6379"));
    private static final String REDIS_HOT_NOTES_KEY = System.getenv().getOrDefault("RECOMMEND_HOT_NOTES_KEY", "hot_notes");
    private static final String REDIS_FUSED_PROFILE_PREFIX = System.getenv().getOrDefault("RECOMMEND_FUSED_PROFILE_KEY_PREFIX", "user_fused_profile:");
    private static final String REDIS_RECENT_SEARCH_PREFIX = System.getenv().getOrDefault("RECOMMEND_RECENT_SEARCH_TERMS_KEY_PREFIX", "user_recent_search_terms:");
    private static final String REDIS_SEEN_PREFIX = System.getenv().getOrDefault("RECOMMEND_SEEN_ITEMS_KEY_PREFIX", "user_seen_items:");
    private static final String REDIS_RECENT_ACTION_PREFIX = System.getenv().getOrDefault("RECOMMEND_RECENT_ACTIONS_KEY_PREFIX", "user_recent_actions:");
    private static final String REDIS_ITEM_STATS_PREFIX = System.getenv().getOrDefault("RECOMMEND_ITEM_REALTIME_STATS_KEY_PREFIX", "item_realtime_stats:");

    private static final String KAFKA_BOOTSTRAP = System.getenv().getOrDefault("RECOMMEND_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    private static final String KAFKA_GROUP = System.getenv().getOrDefault("RECOMMEND_KAFKA_GROUP_ID", "recommend-flink-realtime");
    private static final String KAFKA_BEHAVIOR_TOPIC = System.getenv().getOrDefault("RECOMMEND_TOPIC_BEHAVIOR", "rec_user_behavior");
    private static final String KAFKA_SEARCH_TOPIC = System.getenv().getOrDefault("RECOMMEND_TOPIC_SEARCH", "rec_user_search");
    private static final String KAFKA_EXPOSURE_TOPIC = System.getenv().getOrDefault("RECOMMEND_TOPIC_EXPOSURE", "rec_user_exposure");

    private static final int TOP_N = 10;
    private static final String FLINK_TARGET = System.getenv().getOrDefault("FLINK_EXECUTION_TARGET", "remote");
    private static final String FLINK_REST_ADDRESS = System.getenv().getOrDefault("FLINK_REST_ADDRESS", "127.0.0.1");
    private static final int FLINK_REST_PORT = Integer.parseInt(System.getenv().getOrDefault("FLINK_REST_PORT", "8081"));

    public static void main(String[] args) throws Exception {

        // -------------------- Flink 集群配置 --------------------
        // 默认依赖独立 Flink 集群（remote target），可通过环境变量覆盖:
        // FLINK_EXECUTION_TARGET=remote|local
        // FLINK_REST_ADDRESS=<jobmanager-rest-address>
        // FLINK_REST_PORT=<jobmanager-rest-port>
        Configuration conf = new Configuration();
        applyMiniClusterResourceTuning(conf);
        StreamExecutionEnvironment env;

        if ("remote".equalsIgnoreCase(FLINK_TARGET)) {
            conf.set(DeploymentOptions.TARGET, "remote");
            conf.set(RestOptions.ADDRESS, FLINK_REST_ADDRESS);
            conf.set(RestOptions.PORT, FLINK_REST_PORT);
            env = StreamExecutionEnvironment.getExecutionEnvironment(conf);
            System.out.printf("Using remote Flink cluster: %s:%d%n", FLINK_REST_ADDRESS, FLINK_REST_PORT);
        } else {
            // 回退: 本地模式仅用于开发排错
            int slotNum = 4;
            conf.setInteger("taskmanager.numberOfTaskSlots", slotNum);
            env = StreamExecutionEnvironment.createLocalEnvironment(slotNum, conf);
            System.out.println("Using local Flink environment.");
        }

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics(KAFKA_BEHAVIOR_TOPIC, KAFKA_SEARCH_TOPIC, KAFKA_EXPOSURE_TOPIC)
                .setGroupId(KAFKA_GROUP)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new org.apache.flink.api.common.serialization.SimpleStringSchema())
                .build();

        var eventStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "recommend-kafka-source")
                .process(new ProcessFunction<String, RecommendEvent>() {
                    @Override
                    public void processElement(String value, ProcessFunction<String, RecommendEvent>.Context ctx, Collector<RecommendEvent> out) {
                        try {
                            RecommendEvent event = objectMapper.readValue(value, RecommendEvent.class);
                            if (event.getUserId() != null && event.getEventType() != null) {
                                out.collect(event);
                            }
                        } catch (Exception ignored) {
                            // ignore malformed event
                        }
                    }
                });

        // -------------------- 用户行为流 --------------------
        var behaviorStream = eventStream
                .filter(event -> event.getEventType() != RecommendEventType.SEARCH
                        && event.getItemType() == ItemType.NOTE
                        && event.getItemId() != null
                        && !event.getItemId().isBlank())
                .map(event -> {
                    String tagValue = event.getTags() == null ? "" : String.join(",", event.getTags());
                    int weight = eventWeight(event.getEventType());
                    return new UserBehaviorEvent(
                            event.getUserId(),
                            Long.valueOf(event.getItemId()),
                            tagValue,
                            weight,
                            event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp()
                    );
                })
                .map(event -> new Tuple2<>(event.userId(), event))
                .returns(TypeInformation.of(new TypeHint<Tuple2<Long, UserBehaviorEvent>>() {}));

        // 复制一个流用于 Top-N 热点计算
        var topNStream = behaviorStream
                .map(tuple -> tuple.f1); // 取 UserBehaviorEvent

        // -------------------- 输出用户行为 --------------------
        behaviorStream.map(tuple -> "BehaviorEvent: " + tuple.f1).print();

        // -------------------- 用户搜索流 --------------------
        var searchStream = eventStream
                .filter(event -> event.getEventType() == RecommendEventType.SEARCH
                        && event.getKeyword() != null
                        && !event.getKeyword().isBlank())
                .map(event -> new UserSearchEvent(
                        event.getUserId(),
                        event.getKeyword(),
                        event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp()
                ))
                .map(event -> new Tuple2<>(event.userId(), event))
                .returns(TypeInformation.of(new TypeHint<Tuple2<Long, UserSearchEvent>>() {}));

        searchStream.map(tuple -> "SearchEvent: " + tuple.f1).print();

        // -------------------- 实时状态落 Redis --------------------
        eventStream.process(new ProcessFunction<RecommendEvent, Void>() {
            @Override
            public void processElement(RecommendEvent event, ProcessFunction<RecommendEvent, Void>.Context ctx, Collector<Void> out) throws Exception {
                Long userId = event.getUserId();
                if (userId == null) {
                    return;
                }
                try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
                    if (event.getEventType() == RecommendEventType.SEARCH && event.getKeyword() != null && !event.getKeyword().isBlank()) {
                        String searchKey = REDIS_RECENT_SEARCH_PREFIX + userId;
                        jedis.lpush(searchKey, event.getKeyword());
                        jedis.ltrim(searchKey, 0, 199);
                    }

                    if (event.getItemType() != null && event.getItemId() != null && !event.getItemId().isBlank()) {
                        String itemKey = event.getItemType().name() + ":" + event.getItemId();
                        jedis.sadd(REDIS_SEEN_PREFIX + userId, itemKey);
                        if (event.getEventType() != RecommendEventType.IMPRESSION) {
                            jedis.lpush(REDIS_RECENT_ACTION_PREFIX + userId, event.getEventType().name() + "|" + itemKey);
                            jedis.ltrim(REDIS_RECENT_ACTION_PREFIX + userId, 0, 199);
                        }
                    }
                }
            }
        });

        // -------------------- 用户画像融合 --------------------
        behaviorStream
                .keyBy(tuple -> tuple.f0)
                .connect(searchStream.keyBy(tuple -> tuple.f0))
                .process(new UserProfileFusionFunction())
                .map(fused -> "FusedProfile: " + fused)
                .print();

        // -------------------- Top-N 热点计算 --------------------
        topNStream
                // 不按 noteId keyBy，而是使用全局 key（所有事件合并计算 Top-N）
                .keyBy(e -> 0) // 所有事件在同一个 key 上
                .process(new KeyedProcessFunction<Integer, UserBehaviorEvent, Void>() {

                    private ListState<UserBehaviorEvent> state;

                    @Override
                    public void open(org.apache.flink.configuration.Configuration parameters) {
                        ListStateDescriptor<UserBehaviorEvent> desc =
                                new ListStateDescriptor<>("behaviorListState", UserBehaviorEvent.class);
                        state = getRuntimeContext().getListState(desc);
                    }

                    @Override
                    public void processElement(UserBehaviorEvent value, Context ctx, Collector<Void> out) throws Exception {
                        state.add(value);
                        // 每 10 秒更新一次 Top-N
                        ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 10000);
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Void> out) throws Exception {
                        long oneHourAgo = System.currentTimeMillis() - 3600_000L; // 1小时

                        List<UserBehaviorEvent> recentEvents = new ArrayList<>();
                        for (UserBehaviorEvent e : state.get()) {
                            if (e.timestamp() >= oneHourAgo) {
                                recentEvents.add(e);
                            }
                        }

                        // 更新状态，只保留最近1小时事件
                        state.update(recentEvents);

                        // 累加笔记权重
                        Map<Long, Integer> scoreMap = recentEvents.stream()
                                .collect(Collectors.groupingBy(UserBehaviorEvent::noteId,
                                        Collectors.summingInt(UserBehaviorEvent::weight)));

                        // Top-N
                        List<Long> topN = scoreMap.entrySet().stream()
                                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                                .limit(TOP_N)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList());

                        // 打印 Top-N 笔记 ID
                        System.out.println("Writing Top-N to Redis: " + topN);

                        // 写入 Redis
                        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
                            jedis.set(REDIS_HOT_NOTES_KEY, objectMapper.writeValueAsString(topN));
                            for (Map.Entry<Long, Integer> entry : scoreMap.entrySet()) {
                                Map<String, Object> stats = new HashMap<>();
                                stats.put("hotScore", entry.getValue());
                                stats.put("updatedAt", System.currentTimeMillis());
                                jedis.set(REDIS_ITEM_STATS_PREFIX + "NOTE:" + entry.getKey(), objectMapper.writeValueAsString(stats));
                            }
                        }
                    }

                });

        env.execute("Behavior + Search Stream with Fusion and Real-time Top-N Hot Notes");
    }

    /**
     * MiniCluster 默认 TaskManager 总内存较小，多 source + connect + keyBy 时容易触发
     * {@code Insufficient number of network buffers}，需显式放大 process / network 相关配置。
     */
    private static void applyMiniClusterResourceTuning(Configuration conf) {
        conf.setString("taskmanager.memory.process.size", "2048m");
        conf.setString("taskmanager.memory.network.min", "512m");
        conf.setString("taskmanager.memory.network.max", "512m");
        conf.setString("taskmanager.memory.network.fraction", "0.25");
        conf.setString("taskmanager.memory.segment-size", "32768");
    }

    private static int eventWeight(RecommendEventType eventType) {
        return switch (eventType) {
            case COMMENT -> 4;
            case FAVORITE -> 3;
            case LIKE, FOLLOW -> 2;
            case CLICK, VIEW -> 1;
            default -> 1;
        };
    }
}
