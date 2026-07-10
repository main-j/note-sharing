package com.project.login.service.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.model.dto.search.NoteSearchDTO;
import com.project.login.model.vo.NoteSearchVO;
import com.project.login.model.vo.qa.QuestionVO;
import com.project.login.service.hot.HotService;
import com.project.login.service.qa.QuestionService;
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
    private final HotService hotService;
    private final QuestionService questionService;

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
        if (keywords.isEmpty()) {
            return hotService.getHotNotesDetail().stream().limit(topN).collect(Collectors.toList());
        }

        Map<Long, NoteSearchVO> uniqueNotes = new LinkedHashMap<>();

        for (String keyword : keywords) {
            NoteSearchDTO dto = new NoteSearchDTO();
            dto.setKeyword(keyword);
            List<NoteSearchVO> results = searchService.searchNotes(dto);

            results.forEach(vo -> uniqueNotes.putIfAbsent(vo.getNoteId(), vo));
        }

        List<NoteSearchVO> merged = new ArrayList<>(uniqueNotes.values());
        merged.sort((a, b) -> {
            double scoreA = noteScore(a);
            double scoreB = noteScore(b);
            return Double.compare(scoreB, scoreA);
        });

        if (merged.isEmpty()) {
            merged = hotService.getHotNotesDetail();
        }

        return merged.stream().limit(topN).collect(Collectors.toList());
    }

    /**
     * 根据用户画像关键词推荐问答（QA），并去重
     */
    public List<QuestionVO> recommendQuestionsByKeywords(Long userId, int topN) throws Exception {
        List<String> keywords = getTopKeywords(userId, topN);
        if (keywords.isEmpty()) {
            return questionService.getAllQuestions().stream().limit(topN).collect(Collectors.toList());
        }

        Map<String, QuestionVO> uniqueQuestions = new LinkedHashMap<>();

        for (String keyword : keywords) {
            List<QuestionVO> results = qaService.searchQuestions(keyword);
            results.forEach(vo -> uniqueQuestions.putIfAbsent(vo.getQuestionId(), vo));
        }

        List<QuestionVO> merged = new ArrayList<>(uniqueQuestions.values());
        merged.sort((a, b) -> {
            double scoreA = qaScore(a);
            double scoreB = qaScore(b);
            return Double.compare(scoreB, scoreA);
        });

        if (merged.isEmpty()) {
            merged = questionService.getAllQuestions();
        }

        return merged.stream().limit(topN).collect(Collectors.toList());
    }

    private double noteScore(NoteSearchVO vo) {
        return safeInt(vo.getViewCount())
                + safeInt(vo.getLikeCount())
                + safeInt(vo.getFavoriteCount())
                + safeInt(vo.getCommentCount());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double qaScore(QuestionVO vo) {
        int answers = vo.getAnswerCount() != null
                ? vo.getAnswerCount()
                : (vo.getAnswers() != null ? vo.getAnswers().size() : 0);
        int likes = vo.getLikeCount() != null ? vo.getLikeCount() : 0;
        int favorites = vo.getFavoriteCount() != null ? vo.getFavoriteCount() : 0;
        return answers + likes + favorites;
    }


}
