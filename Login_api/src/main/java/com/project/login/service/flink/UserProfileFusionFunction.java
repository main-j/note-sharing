package com.project.login.service.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.model.event.UserBehaviorEvent;
import com.project.login.model.event.UserSearchEvent;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.api.java.tuple.Tuple2;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class UserProfileFusionFunction
        extends KeyedCoProcessFunction<Long,
        Tuple2<Long, UserBehaviorEvent>,
        Tuple2<Long, UserSearchEvent>,
        String> {

    private MapState<String, Integer> behaviorProfile;
    private MapState<String, Integer> searchProfile;

    // Redis 配置
    private final String redisHost = System.getenv().getOrDefault("RECOMMEND_REDIS_HOST", "localhost");
    private final int redisPort = Integer.parseInt(System.getenv().getOrDefault("RECOMMEND_REDIS_PORT", "6379"));
    private final String fusedProfilePrefix = System.getenv().getOrDefault("RECOMMEND_FUSED_PROFILE_KEY_PREFIX", "user_fused_profile:");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) {
        MapStateDescriptor<String, Integer> behaviorDesc =
                new MapStateDescriptor<>("behaviorProfile", String.class, Integer.class);
        MapStateDescriptor<String, Integer> searchDesc =
                new MapStateDescriptor<>("searchProfile", String.class, Integer.class);

        behaviorProfile = getRuntimeContext().getMapState(behaviorDesc);
        searchProfile = getRuntimeContext().getMapState(searchDesc);
    }

    @Override
    public void processElement1(
            Tuple2<Long, UserBehaviorEvent> tuple,
            Context ctx,
            Collector<String> out) throws Exception {

        UserBehaviorEvent event = tuple.f1;

        // 不拆 tags，直接作为一个字符串
        String tag = event.tags();
        Integer oldValue = behaviorProfile.get(tag);
        if (oldValue == null) oldValue = 0;
        behaviorProfile.put(tag, oldValue + event.weight());

        Map<String, Double> fused = fuseProfile();
        writeToRedis(ctx.getCurrentKey(), fused);

        out.collect("Updated fused profile for user " + ctx.getCurrentKey());
    }


    @Override
    public void processElement2(
            Tuple2<Long, UserSearchEvent> tuple,
            Context ctx,
            Collector<String> out) throws Exception {

        UserSearchEvent event = tuple.f1;

        Integer oldCount = searchProfile.get(event.keyword());
        if (oldCount == null) oldCount = 0;
        searchProfile.put(event.keyword(), oldCount + 1);

        Map<String, Double> fused = fuseProfile();
        writeToRedis(ctx.getCurrentKey(), fused);

        out.collect("Updated fused profile for user " + ctx.getCurrentKey());
    }

    // 融合行为画像和搜索画像
    private Map<String, Double> fuseProfile() throws Exception {
        Map<String, Double> fused = new HashMap<>();

        double wSearch = 0.6;
        for (String key : searchProfile.keys()) {
            fused.put(key, searchProfile.get(key) * wSearch);
        }

        double wBehavior = 0.4;
        for (String key : behaviorProfile.keys()) {
            fused.merge(key, behaviorProfile.get(key) * wBehavior, Double::sum);
        }

        return fused;
    }

    // 写入 Redis
    private void writeToRedis(Long userId, Map<String, Double> fused) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            String key = fusedProfilePrefix + userId;
            jedis.set(key, objectMapper.writeValueAsString(fused));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
