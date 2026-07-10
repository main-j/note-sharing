package com.project.login.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.convert.QuestionConvert;
import com.project.login.model.dataobject.QuestionDO;
import com.project.login.model.vo.qa.QuestionVO;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQAService {

    private final ElasticsearchClient esClient;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final QuestionConvert convert;
    private final ObjectMapper objectMapper;
    @Resource
    private StringRedisTemplate redis;

    private static final String REDIS_KEY_PREFIX = "question:detail:";

    public List<QuestionVO> searchQuestions(String keyword) {

        List<ScoredQuestion> esHits = new ArrayList<>();

        // ES 搜索，只获取 questionId
        try {
            var response = esClient.search(s -> s
                            .index("questions")
                            .size(30)
                            .query(q -> q
                                    .multiMatch(m -> m
                                            .query(keyword)
                                            .fields("title^3", "content", "tags^2")
                                    )
                            ),
                    Object.class
            );

            response.hits().hits().forEach(hit -> {
                String questionId = resolveQuestionId(hit.source(), hit.id());
                if (questionId == null) {
                    return;
                }

                double score = hit.score() != null ? hit.score() : 0;
                esHits.add(new ScoredQuestion(questionId, score));
            });

        } catch (IOException e) {
            throw new RuntimeException("ES 搜索失败", e);
        }

        List<ScoredQuestion> scoredList = dedupeByQuestionId(esHits);

        if (scoredList.isEmpty()) {
            scoredList = searchMongoFallback(keyword);
        }

        if (scoredList.isEmpty()) return Collections.emptyList();

        // 批量加载问答数据（Redis → MongoDB）
        List<String> qids = scoredList.stream()
                .map(s -> s.questionId)
                .toList();

        Map<String, QuestionDO> detailMap = loadQuestionDetailBatch(qids);

        // 转换成 VO，并计算最新活跃时间
        scoredList.forEach(s -> {
            QuestionDO data = detailMap.get(s.questionId);
            if (data != null) {
                s.vo = convert.toQuestionSearchVO(data);
                s.updatedAt = getLatestActivity(data);
            }
        });

        // 综合排序：ES score + 点赞/收藏/回答数（跳过 Mongo 未命中的条目）
        scoredList.removeIf(s -> s.vo == null);
        scoredList.sort((a, b) -> {
            int answersA = a.vo.getAnswers() != null ? a.vo.getAnswers().size()
                    : (a.vo.getAnswerCount() != null ? a.vo.getAnswerCount() : 0);
            int answersB = b.vo.getAnswers() != null ? b.vo.getAnswers().size()
                    : (b.vo.getAnswerCount() != null ? b.vo.getAnswerCount() : 0);
            double sa = a.score * 4
                    + safeInt(a.vo.getLikeCount()) * 2
                    + safeInt(a.vo.getFavoriteCount()) * 3
                    + answersA
                    + recencyScore(a.updatedAt);

            double sb = b.score * 4
                    + safeInt(b.vo.getLikeCount()) * 2
                    + safeInt(b.vo.getFavoriteCount()) * 3
                    + answersB
                    + recencyScore(b.updatedAt);

            return Double.compare(sb, sa);
        });

        return scoredList.stream()
                .map(s -> s.vo)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private String resolveQuestionId(Object source, String documentId) {
        if (source instanceof Map<?, ?> src) {
            Object rawId = src.get("questionId");
            if (rawId != null) {
                String questionId = String.valueOf(rawId).trim();
                if (!questionId.isBlank()) {
                    return questionId;
                }
            }
        }
        if (documentId == null || documentId.isBlank()) {
            return null;
        }
        return documentId.trim();
    }

    private List<ScoredQuestion> dedupeByQuestionId(List<ScoredQuestion> scoredList) {
        Map<String, ScoredQuestion> deduped = new LinkedHashMap<>();
        for (ScoredQuestion scoredQuestion : scoredList) {
            if (scoredQuestion.questionId == null || scoredQuestion.questionId.isBlank()) {
                continue;
            }
            deduped.merge(
                    scoredQuestion.questionId,
                    scoredQuestion,
                    (left, right) -> left.score >= right.score ? left : right
            );
        }
        return new ArrayList<>(deduped.values());
    }

    private List<ScoredQuestion> searchMongoFallback(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        String escaped = Pattern.quote(trimmed);
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("title").regex(escaped, "i"),
                Criteria.where("content").regex(escaped, "i"),
                Criteria.where("tags").regex(escaped, "i")
        );
        Query query = new Query(criteria).limit(30);
        List<QuestionDO> questions = mongoTemplate.find(query, QuestionDO.class);
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredQuestion> scoredList = new ArrayList<>(questions.size());
        for (int i = 0; i < questions.size(); i++) {
            QuestionDO question = questions.get(i);
            if (question.getQuestionId() == null || question.getQuestionId().isBlank()) {
                continue;
            }
            scoredList.add(new ScoredQuestion(question.getQuestionId(), 1.0 - (i * 0.01)));
        }
        log.info("QA search ES miss, Mongo fallback hit {} items for keyword={}", scoredList.size(), trimmed);
        return scoredList;
    }

    // 批量加载 Redis → MongoDB
    private Map<String, QuestionDO> loadQuestionDetailBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, QuestionDO> result = new HashMap<>();
        List<String> missingIds = new ArrayList<>();

        for (String qid : ids) {
            if (qid == null || qid.isBlank()) {
                continue;
            }
            String key = REDIS_KEY_PREFIX + qid;

            try {
                String json = redis.opsForValue().get(key);
                if (json != null) {
                    QuestionDO q = objectMapper.readValue(json, QuestionDO.class);
                    result.put(qid, q);
                    continue;
                }
            } catch (Exception e) {
                log.warn("Redis 问答缓存解析失败 qid={}", qid, e);
            }

            missingIds.add(qid);
        }

        if (!missingIds.isEmpty()) {
            Query query = new Query(Criteria.where("questionId").in(missingIds));
            List<QuestionDO> docs = mongoTemplate.find(query, QuestionDO.class);
            for (QuestionDO doc : docs) {
                if (doc.getQuestionId() == null || doc.getQuestionId().isBlank()) {
                    continue;
                }
                result.put(doc.getQuestionId(), doc);
                try {
                    String dbJson = objectMapper.writeValueAsString(doc);
                    redis.opsForValue().set(REDIS_KEY_PREFIX + doc.getQuestionId(), dbJson, Duration.ofHours(2));
                } catch (Exception e) {
                    log.warn("写入 Redis 问答缓存失败 qid={}", doc.getQuestionId(), e);
                }
            }
        }

        return result;
    }

    // 提问、回答、评论、回复中最晚的时间
    private LocalDateTime getLatestActivity(QuestionDO q) {
        if (q == null) return null;

        LocalDateTime latest = q.getCreatedAt();
        if (q.getAnswers() == null) {
            return latest;
        }

        for (var ans : q.getAnswers()) {
            latest = max(latest, ans.getCreatedAt());

            if (ans.getComments() == null) {
                continue;
            }
            for (var c : ans.getComments()) {
                latest = max(latest, c.getCreatedAt());

                if (c.getReplies() == null) {
                    continue;
                }
                for (var r : c.getReplies()) {
                    latest = max(latest, r.getCreatedAt());
                }
            }
        }
        return latest;
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private double recencyScore(LocalDateTime time) {
        if (time == null) return 0;

        long millis = Duration.between(time, LocalDateTime.now()).toMillis();
        double days = millis / 86400000.0;
        return 1 / (days + 1);
    }

    private static class ScoredQuestion {
        String questionId;
        double score;
        QuestionVO vo;
        LocalDateTime updatedAt;

        ScoredQuestion(String qid, double score) {
            this.questionId = qid;
            this.score = score;
        }
    }
}


