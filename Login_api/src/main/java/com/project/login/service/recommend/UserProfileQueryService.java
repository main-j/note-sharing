package com.project.login.service.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.model.dto.search.NoteSearchDTO;
import com.project.login.model.vo.NoteSearchVO;
import com.project.login.model.vo.qa.QuestionVO;
import com.project.login.service.search.SearchQAService;
import com.project.login.service.search.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileQueryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SearchService searchService;
    private final SearchQAService qaService;

    private List<String> getTopKeywords(Long userId, int topN) throws Exception {
        String json = redisTemplate.opsForValue().get("user_fused_profile:" + userId);
        if (json == null) return Collections.emptyList();

        Map<String, Double> fused = objectMapper.readValue(json, new TypeReference<>() {
        });
        return fused.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 根据用户画像关键词推荐笔记，并去重
     */
    public List<NoteSearchVO> recommendNotesByKeywords(Long userId, int topN) throws Exception {
        List<String> keywords = getTopKeywords(userId, topN);
        if (keywords.isEmpty()) return Collections.emptyList();

        Map<Long, NoteSearchVO> uniqueNotes = new LinkedHashMap<>();

        for (String keyword : keywords) {
            NoteSearchDTO dto = new NoteSearchDTO();
            dto.setKeyword(keyword);
            List<NoteSearchVO> results = searchService.searchNotes(dto);

            results.forEach(vo -> uniqueNotes.putIfAbsent(vo.getNoteId(), vo));
        }

        List<NoteSearchVO> merged = new ArrayList<>(uniqueNotes.values());
        merged.sort((a, b) -> {
            double scoreA = a.getViewCount() + a.getLikeCount() + a.getFavoriteCount() + a.getCommentCount();
            double scoreB = b.getViewCount() + b.getLikeCount() + b.getFavoriteCount() + b.getCommentCount();
            return Double.compare(scoreB, scoreA); });

        return merged;
    }

    /**
     * 根据用户画像关键词推荐问答（QA），并去重
     */
    public List<QuestionVO> recommendQuestionsByKeywords(Long userId, int topN) throws Exception {
        List<String> keywords = getTopKeywords(userId, topN);
        if (keywords.isEmpty()) return Collections.emptyList();

        Map<String, QuestionVO> uniqueQuestions = new LinkedHashMap<>();

        for (String keyword : keywords) {
            List<QuestionVO> results = qaService.searchQuestions(keyword);
            results.forEach(vo -> uniqueQuestions.putIfAbsent(vo.getQuestionId(), vo));
        }

        List<QuestionVO> merged = new ArrayList<>(uniqueQuestions.values());
        merged.sort((a, b) -> {
            double scoreA = a.getAnswers().size() + a.getLikeCount() + a.getFavoriteCount();
            double scoreB = b.getAnswers().size() + b.getLikeCount() + b.getFavoriteCount();
            return Double.compare(scoreB, scoreA);
        });

        return merged;
    }


}
