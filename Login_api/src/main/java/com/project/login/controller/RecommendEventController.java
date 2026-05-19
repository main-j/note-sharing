package com.project.login.controller;

import com.project.login.model.response.StandardResponse;
import com.project.login.service.recommend.event.InteractionEventService;
import com.project.login.service.recommend.event.model.InteractionEventRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommend/events")
@RequiredArgsConstructor
public class RecommendEventController {

    private final InteractionEventService interactionEventService;

    @PostMapping("/interaction")
    public StandardResponse<Boolean> recordInteraction(@Valid @RequestBody InteractionEventRequest request) {
        interactionEventService.recordInteraction(request);
        return StandardResponse.success(true);
    }
}
