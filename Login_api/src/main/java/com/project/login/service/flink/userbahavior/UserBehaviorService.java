package com.project.login.service.flink.userbahavior;

import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.NotebookMapper;
import com.project.login.mapper.TagMapper;
import com.project.login.model.dto.userbehavior.BehaviorType;
import com.project.login.model.dto.userbehavior.UserBehaviorDTO;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.event.BehaviorEventProducer;
import com.project.login.service.recommend.event.RecommendEventType;
import com.project.login.service.recommend.event.model.RecommendEvent;
import com.project.login.service.recommend.model.ItemType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorService {

    private final StringRedisTemplate redisTemplate;
    private final BehaviorEventProducer behaviorEventProducer;
    private final RecommendationInfraProperties infraProperties;
    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper notespaceMapper;
    private final TagMapper tagMapper;

    private static final String STREAM_KEY = "user_behavior_stream";

    public void recordBehavior(UserBehaviorDTO dto) {

        if (dto == null || dto.getUserId() == null || dto.getBehaviorType() == null) {
            log.warn("Invalid user behavior: {}", dto);
            return;
        }

        long ts = (dto.getTimestamp() != null)
                ? dto.getTimestamp()
                : System.currentTimeMillis();

        // 1. 查 notebookId
        Long notebookId = noteMapper.selectNotebookIdByNoteId(dto.getTargetId());
        if (notebookId == null) {
            log.warn("No notebook found for noteId={}, skip behavior", dto.getTargetId());
            return;
        }

        // 2. 查 spaceId
        Long spaceId = notebookMapper.selectSpaceIdByNotebookId(notebookId);
        if (spaceId == null) {
            log.warn("No space found for notebookId={}, skip behavior", notebookId);
            return;
        }

        // 3. 查 tagId
        Long tagIdNotebook = notebookMapper.selectNotebookTagIdByNotebookId(notebookId);
        Long tagIdSpace = notespaceMapper.selectSpaceTagIdBySpaceId(spaceId);

        // 4. 查 tag 名
        String tagOfNotebook = (tagIdNotebook != null) ? tagMapper.selectNameById(tagIdNotebook) : null;
        String tagOfSpace = (tagIdSpace != null) ? tagMapper.selectNameById(tagIdSpace) : null;

        List<String> tags = Stream.of(tagOfNotebook, tagOfSpace)
                .filter(Objects::nonNull)
                .toList();

        int weight = switch (dto.getBehaviorType()) {
            case VIEW -> 1;
            case LIKE -> 2;
            case FAVORITE -> 3;
            case COMMENT -> 4;
            default -> 1;
        };

        // 写入 Redis Stream（Flink 直接消费）
        Map<String, String> map = new HashMap<>();
        map.put("user_id", String.valueOf(dto.getUserId()));
        map.put("event_type", dto.getBehaviorType().name());
        map.put("target_id", String.valueOf(dto.getTargetId()));
        map.put("timestamp", String.valueOf(ts));

        // 额外提供给推荐系统的关键字段
        String tag = String.join(",", tags);
        System.out.println(tag);
        map.put("tags", tag);     // 用户兴趣特征
        map.put("weight", String.valueOf(weight));   // 行为强度

        if (infraProperties.getEvent().isLegacyRedisStreamEnabled()) {
            redisTemplate.opsForStream().add(STREAM_KEY, map);
        }

        RecommendEvent event = RecommendEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(dto.getUserId())
                .itemType(ItemType.NOTE)
                .itemId(String.valueOf(dto.getTargetId()))
                .eventType(mapBehaviorType(dto.getBehaviorType()))
                .tags(tags)
                .timestamp(ts)
                .build();
        behaviorEventProducer.send(event);

        log.info("User behavior recorded for Flink/Kafka: {}", map);
    }

    private RecommendEventType mapBehaviorType(BehaviorType behaviorType) {
        return switch (behaviorType) {
            case VIEW -> RecommendEventType.VIEW;
            case LIKE -> RecommendEventType.LIKE;
            case FAVORITE -> RecommendEventType.FAVORITE;
            case COMMENT -> RecommendEventType.COMMENT;
        };
    }
}
