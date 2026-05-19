package com.project.login.service.recommend.event;

import com.project.login.service.recommend.config.RecommendationInfraProperties;
import com.project.login.service.recommend.event.model.RecommendEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentEventProducer {

    private final RecommendEventProducer delegate;
    private final RecommendationInfraProperties infraProperties;

    public void send(RecommendEvent event) {
        delegate.send(infraProperties.getTopic().getContent(), event);
    }
}
