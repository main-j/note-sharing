package com.project.login.service.recommend.rank;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.model.ContentCandidate;
import com.project.login.service.recommend.model.RecommendContext;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation.infra.onnx", name = "enabled", havingValue = "true")
public class OnnxRanker implements Ranker {

    private final RecommendationInfraProperties infraProperties;

    private volatile OrtEnvironment environment;
    private volatile OrtSession session;

    @Override
    public List<ContentCandidate> rank(RecommendContext context, List<ContentCandidate> candidates) {
        try {
            OrtSession activeSession = getSession();
            if (activeSession == null) {
                return candidates;
            }
            String inputName = activeSession.getInputNames().iterator().next();
            String outputName = activeSession.getOutputNames().iterator().next();

            for (ContentCandidate candidate : candidates) {
                float[][] features = new float[][]{
                        new float[]{
                                feature(candidate, "views"),
                                feature(candidate, "likes"),
                                feature(candidate, "favorites"),
                                feature(candidate, "comments"),
                                feature(candidate, "answers"),
                                feature(candidate, "tagMatchScore"),
                                (float) candidate.getRecallScore()
                        }
                };
                try (OnnxTensor tensor = OnnxTensor.createTensor(environment, features)) {
                    Map<String, OnnxTensor> inputs = new HashMap<>();
                    inputs.put(inputName, tensor);
                    try (OrtSession.Result result = activeSession.run(inputs)) {
                        Object out = result.get(outputName).get().getValue();
                        float score = extractScore(out);
                        candidate.getFeatures().put("onnxRankScore", score);
                        candidate.setRankScore(score);
                    }
                }
            }
        } catch (Exception ignored) {
            // fall back to downstream rankers
        }
        return candidates;
    }

    private synchronized OrtSession getSession() throws OrtException {
        if (session != null) {
            return session;
        }
        Path modelPath = Path.of(infraProperties.getOnnx().getModelPath());
        if (!Files.exists(modelPath)) {
            return null;
        }
        environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        session = environment.createSession(modelPath.toString(), options);
        return session;
    }

    private float feature(ContentCandidate candidate, String key) {
        Object value = candidate.getFeatures().get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return 0F;
    }

    private float extractScore(Object out) {
        if (out instanceof float[][] matrix && matrix.length > 0 && matrix[0].length > 0) {
            return matrix[0][0];
        }
        if (out instanceof float[] vector && vector.length > 0) {
            return vector[0];
        }
        return 0F;
    }

    @PreDestroy
    public synchronized void close() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }
}
