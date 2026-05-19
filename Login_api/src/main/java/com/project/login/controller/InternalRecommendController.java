package com.project.login.controller;

import com.project.login.model.response.StandardResponse;
import com.project.login.service.recommend.mlops.RecommendMlopsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/recommend")
@RequiredArgsConstructor
public class InternalRecommendController {

    private final RecommendMlopsClient recommendMlopsClient;

    @PostMapping("/train")
    public StandardResponse<Map<String, Object>> triggerTraining(
            @RequestParam(value = "runExport", defaultValue = "false") boolean runExport) {
        return StandardResponse.success(recommendMlopsClient.triggerTraining(runExport));
    }

    @GetMapping("/status")
    public StandardResponse<Map<String, Object>> trainingStatus() {
        return StandardResponse.success(recommendMlopsClient.status());
    }
}
