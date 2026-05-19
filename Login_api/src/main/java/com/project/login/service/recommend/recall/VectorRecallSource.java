package com.project.login.service.recommend.recall;

import com.project.login.service.recommend.config.RecommendationFeedProperties;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.ItemType;
import com.project.login.service.recommend.model.RecommendContext;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation.infra.milvus", name = "enabled", havingValue = "true")
public class VectorRecallSource implements RecallSource {

    private final RecommendationInfraProperties infraProperties;
    private final RecommendationFeedProperties feedProperties;

    @Override
    public List<ContentCandidate> recall(RecommendContext context) {
        MilvusServiceClient client = createClient();
        try {
            List<Float> queryVector = buildQueryVector(context);
            int topK = Math.min(feedProperties.getMaxRecallPerSource(), infraProperties.getMilvus().getTopK());
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(infraProperties.getMilvus().getCollectionName())
                    .withMetricType(MetricType.IP)
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withOutFields(List.of("item_type", "item_id"))
                    .build();
            R<SearchResults> response = client.search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode() || response.getData() == null) {
                return List.of();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            if (scores == null || scores.isEmpty()) {
                return List.of();
            }

            List<?> itemTypes = wrapper.getFieldWrapper("item_type").getFieldData();
            List<?> itemIds = wrapper.getFieldWrapper("item_id").getFieldData();
            List<ContentCandidate> candidates = new ArrayList<>(scores.size());
            for (int i = 0; i < scores.size(); i++) {
                String itemType = itemTypes == null || i >= itemTypes.size()
                        ? "NOTE"
                        : String.valueOf(itemTypes.get(i));
                String itemId = itemIds == null || i >= itemIds.size()
                        ? null
                        : String.valueOf(itemIds.get(i));
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                ItemType resolvedType = "QUESTION".equalsIgnoreCase(itemType) ? ItemType.QUESTION : ItemType.NOTE;
                float score = scores.get(i).getScore();
                candidates.add(ContentCandidate.builder()
                        .itemType(resolvedType)
                        .itemId(itemId)
                        .itemKey(ContentCandidate.buildItemKey(resolvedType, itemId))
                        .source("VECTOR")
                        .recallScore(Math.max(0.1, score))
                        .features(new java.util.HashMap<>())
                        .build());
            }
            return candidates;
        } catch (Exception ex) {
            return List.of();
        } finally {
            client.close();
        }
    }

    private MilvusServiceClient createClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(infraProperties.getMilvus().getHost())
                .withPort(infraProperties.getMilvus().getPort())
                .build();
        return new MilvusServiceClient(connectParam);
    }

    private List<Float> buildQueryVector(RecommendContext context) {
        int dim = Math.max(8, infraProperties.getMilvus().getVectorDim());
        float[] vector = new float[dim];
        List<String> terms = new ArrayList<>();
        terms.addAll(context.getRecentSearchTerms());
        terms.addAll(context.getTopTags());
        if (terms.isEmpty()) {
            terms.add(String.valueOf(context.getUserId()));
        }
        for (String term : terms) {
            int hash = Math.abs(term.hashCode());
            vector[hash % dim] += 1.0f;
        }
        List<Float> result = new ArrayList<>(dim);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }
}
