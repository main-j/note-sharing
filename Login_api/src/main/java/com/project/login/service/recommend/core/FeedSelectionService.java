package com.project.login.service.recommend.core;

import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.FeedItemVO;
import com.project.login.service.recommend.model.RecommendContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedSelectionService {

    public List<FeedItemVO> select(RecommendContext context, List<ContentCandidate> candidates) {
        int pageSize = context.getPageSize() == null ? 20 : context.getPageSize();
        return candidates.stream()
                .limit(pageSize)
                .map(candidate -> toFeedItem(context, candidate))
                .toList();
    }

    private FeedItemVO toFeedItem(RecommendContext context, ContentCandidate candidate) {
        var features = candidate.getFeatures();
        return FeedItemVO.builder()
                .itemType(candidate.getItemType())
                .itemId(candidate.getItemId())
                .authorId(candidate.getAuthorId())
                .title(asString(features.get("title")))
                .summary(asString(features.get("summary")))
                .authorName(asString(features.get("authorName")))
                .tags(asStringList(features.get("tags")))
                .viewCount(asInteger(features.get("views")))
                .likeCount(asInteger(features.get("likes")))
                .favoriteCount(asInteger(features.get("favorites")))
                .commentCount(asInteger(features.get("comments")))
                .answerCount(asInteger(features.get("answers")))
                .createdAt(asLocalDateTime(features.get("createdAt")))
                .updatedAt(asLocalDateTime(features.get("updatedAt")))
                .reason(candidate.getSource())
                .score(candidate.getFinalScore())
                .experimentId(context.getExperimentId())
                .variant(context.getVariant())
                .modelVersion(context.getModelVersion())
                .ranker(context.isModelEnabled() ? "MODEL" : "RULE")
                .build();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }
}
