package com.project.login.service.sensitive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.mapper.NoteMapper;
import com.project.login.model.dataobject.NoteDO;
import com.project.login.model.vo.SensitiveCheckResult;
import com.project.login.service.minio.MinioService;
import com.project.login.service.noting.ContentSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final NoteMapper noteMapper;
    private final MinioService minioService;
    private final ContentSummaryService contentSummaryService;
    private final FastFilterService fastFilterService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 注入FastFilterService用于调用快速过滤API
    // FastFilterService已在构造函数中注入

    @Value("${modelscope.base-url:https://api-inference.modelscope.cn/v1}")
    private String baseUrl;

    @Value("${modelscope.api.key:}")
    private String apiKey;

    @Value("${modelscope.model:Qwen/Qwen2.5-7B-Instruct}")
    private String modelId;

    @Value("${sensitive.scan.chunk-size:4000}")
    private int chunkSize;

    @Value("${sensitive.scan.concurrency:4}")
    private int scanConcurrency;


    @Override
    public SensitiveCheckResult checkNote(Long noteId) {
        return checkNote(noteId, true); // 统一使用全文检查
    }

    @Override
    public SensitiveCheckResult checkNote(Long noteId, boolean full) {
        NoteDO note = noteMapper.selectById(noteId);
        if (note == null) {
            // 笔记不存在时返回错误结果
            SensitiveCheckResult errorResult = new SensitiveCheckResult();
            errorResult.setStatus("ERROR");
            errorResult.setMessage("内容不存在");
            errorResult.setCheckedAt(Instant.now().toString());
            return errorResult;
        }
        byte[] bytes = minioService.download(note.getFilename());
        
        // 提取文本内容
        String text = contentSummaryService.extractFullText(bytes, note.getFilename());
        
        // 统一使用全文检查（scanWithChunks）
        SensitiveCheckResult res = scanWithChunks(text);
        SensitiveCheckResult.NoteMeta meta = new SensitiveCheckResult.NoteMeta();
        meta.setNoteId(noteId);
        meta.setTitle(note.getTitle());
        res.setNoteMeta(meta);
        return res;
    }

    @Override
    public SensitiveCheckResult checkText(String text) {
        // 使用全文检测逻辑（LLM+文本命中）
        return scanWithChunks(text);
    }

    private JsonNode tryParseJson(String s) {
        try {
            return objectMapper.readTree(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void fillFromJson(SensitiveCheckResult result, JsonNode json) {
        result.setStatus(json.path("status").asText("FLAGGED"));
        result.setRiskLevel(json.path("riskLevel").asText("MEDIUM"));
        result.setScore(json.path("score").isNumber() ? json.path("score").asDouble() : null);

        List<String> cats = new ArrayList<>();
        JsonNode ca = json.path("categories");
        if (ca.isArray()) {
            for (JsonNode c : ca) {
                String v = c.asText();
                if ("敏感词".equals(v) || "粗俗语言".equals(v) || "污言秽语".equals(v)) v = "profanity";
                cats.add(v);
            }
        }
        result.setCategories(cats);

        List<SensitiveCheckResult.Finding> findings = new ArrayList<>();
        JsonNode fa = json.path("findings");
        if (fa.isArray()) {
            for (JsonNode f : fa) {
                SensitiveCheckResult.Finding fi = new SensitiveCheckResult.Finding();
                fi.setTerm(f.path("term").asText(null));
                String cat = f.path("category").asText(null);
                if ("敏感词".equals(cat) || "粗俗语言".equals(cat) || "污言秽语".equals(cat)) cat = "profanity";
                fi.setCategory(cat);
                fi.setConfidence(f.path("confidence").isNumber() ? f.path("confidence").asDouble() : null);
                fi.setSnippet(f.path("snippet").asText(null));
                fi.setStartOffset(f.path("startOffset").isInt() ? f.path("startOffset").asInt() : null);
                fi.setEndOffset(f.path("endOffset").isInt() ? f.path("endOffset").asInt() : null);
                findings.add(fi);
            }
        }
        result.setFindings(findings);
    }

    private String normalizeContent(String s) {
        String t = s == null ? "" : s;
        t = t.replace("```json", "").replace("```", "");
        t = t.replace("\r", "\n");
        return t.trim();
    }

    /**
     * 全文检查：分块并发调用LLM，并使用快速过滤API检测词库命中
     * 应用新的危险等级划分规则：LLM + hits+hitCount>=5（替换10%逻辑）
     */
    private SensitiveCheckResult scanWithChunks(String text) {
        List<String> parts = splitText(text, chunkSize);
        SensitiveCheckResult aggregate = new SensitiveCheckResult();
        aggregate.setStatus("SAFE");
        aggregate.setRiskLevel("LOW");
        aggregate.setScore(0.0);
        aggregate.setCategories(new ArrayList<>());
        aggregate.setFindings(new ArrayList<>());
        long start = System.currentTimeMillis();
        
        // 使用快速过滤API检测词库命中
        List<String> fastFilterHits = fastFilterService.match(text);
        int hitCount = fastFilterHits.size();
        boolean hasFastFilterHit = hitCount >= 5; // hits+hitCount>=5 替换10%逻辑
        
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, scanConcurrency));
        List<Future<SensitiveCheckResult>> futures = new ArrayList<>();
        for (String p : parts) {
            futures.add(pool.submit(() -> callLLMWithoutWordLib(p))); // 使用不包含词库检测的LLM调用
        }
        pool.shutdown();
        for (Future<SensitiveCheckResult> f : futures) {
            try {
                SensitiveCheckResult r = f.get(5, TimeUnit.MINUTES);
                if ("ERROR".equals(r.getStatus())) {
                    aggregate.setStatus("ERROR");
                    aggregate.setMessage(r.getMessage());
                    continue;
                }
                if (aggregate.getModel() == null && r.getModel() != null) aggregate.setModel(r.getModel());
                if ("FLAGGED".equals(r.getStatus())) {
                    aggregate.setStatus("FLAGGED");
                    if (aggregate.getScore() == null || r.getScore() != null && r.getScore() > aggregate.getScore()) {
                        aggregate.setScore(r.getScore());
                    }
                    if ("HIGH".equals(r.getRiskLevel())) aggregate.setRiskLevel("HIGH");
                    else if ("MEDIUM".equals(r.getRiskLevel()) && !"HIGH".equals(aggregate.getRiskLevel())) aggregate.setRiskLevel("MEDIUM");
                    if (r.getCategories() != null) {
                        for (String c : r.getCategories()) {
                            if (!aggregate.getCategories().contains(c)) aggregate.getCategories().add(c);
                        }
                    }
                    if (r.getFindings() != null) aggregate.getFindings().addAll(r.getFindings());
                }
                if (r.getTokenUsage() != null) {
                    SensitiveCheckResult.TokenUsage tu = aggregate.getTokenUsage();
                    if (tu == null) tu = new SensitiveCheckResult.TokenUsage();
                    Integer p1 = r.getTokenUsage().getPromptTokens();
                    Integer c1 = r.getTokenUsage().getCompletionTokens();
                    Integer t1 = r.getTokenUsage().getTotalTokens();
                    tu.setPromptTokens((tu.getPromptTokens() == null ? 0 : tu.getPromptTokens()) + (p1 == null ? 0 : p1));
                    tu.setCompletionTokens((tu.getCompletionTokens() == null ? 0 : tu.getCompletionTokens()) + (c1 == null ? 0 : c1));
                    tu.setTotalTokens((tu.getTotalTokens() == null ? 0 : tu.getTotalTokens()) + (t1 == null ? 0 : t1));
                    aggregate.setTokenUsage(tu);
                }
            } catch (Exception e) {
                aggregate.setStatus("ERROR");
                aggregate.setMessage("并发任务失败");
            }
        }
        
        // 应用新的危险等级划分规则：LLM + hits+hitCount>=5（替换10%逻辑，去除sex.txt逻辑）
        String originalRiskLevel = aggregate.getRiskLevel();
        if (originalRiskLevel == null) {
            originalRiskLevel = "LOW";
        }
        
        // 保存原始风险等级，避免连续升级
        String llmRiskLevel = originalRiskLevel;
        
        // 规则1: 如果LLM返回LOW，hits+hitCount>=5升级为MEDIUM
        if ("LOW".equals(llmRiskLevel) && hasFastFilterHit) {
            aggregate.setRiskLevel("MEDIUM");
            if (aggregate.getStatus() == null || "SAFE".equals(aggregate.getStatus())) {
                aggregate.setStatus("FLAGGED");
            }
            if (aggregate.getScore() == null || aggregate.getScore() < 50.0) {
                aggregate.setScore(50.0);
            }
            log.info("【全文检查】LLM返回LOW，快速过滤命中数{}>=5，升级为MEDIUM", hitCount);
        }
        // 规则2: 如果LLM返回MEDIUM，hits+hitCount>=5升级为HIGH（注意：这里检查的是LLM原始返回，不是升级后的值）
        else if ("MEDIUM".equals(llmRiskLevel) && hasFastFilterHit) {
            aggregate.setRiskLevel("HIGH");
            aggregate.setStatus("FLAGGED");
            if (aggregate.getScore() == null || aggregate.getScore() < 70.0) {
                aggregate.setScore(70.0);
            }
            log.info("【全文检查】LLM返回MEDIUM，快速过滤命中数{}>=5，升级为HIGH", hitCount);
        }
        // 规则3: 如果LLM返回HIGH，保持HIGH（不需要额外处理，已在聚合时设置）
        
        aggregate.setCheckedAt(Instant.now().toString());
        aggregate.setDurationMs(System.currentTimeMillis() - start);
        return aggregate;
    }
    
    /**
     * 调用LLM但不包含词库检测逻辑（用于scanWithChunks，词库检测在外部统一处理）
     */
    private SensitiveCheckResult callLLMWithoutWordLib(String text) {
        long start = System.currentTimeMillis();
        log.info("【LLM审查】开始调用，文本长度: {}", text.length());
        RestTemplate rt = new RestTemplate();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(15000);
        rt.setRequestFactory(rf);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelId);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", "你是一名严格的内容安全审查助手。\n"
                + "任务: 审核用户文本是否包含以下违禁类别，仅在命中时返回命中项。\n"
                + "允许的类别键: profanity, violence, hate, sexual, politics, others。\n"
                + "明确要求: \n"
                + "- 忽略纯技术/编程/拼写错误/库名/代码片段(如 import pandas as pd, pd.readcsv)，这些不属于违禁类别；\n"
                + "- 仅当出现辱骂/仇恨/色情/暴力/政治敏感等真实违规内容时返回 FLAGGED；\n"
                + "- 响应必须是纯JSON，无任何解释或代码围栏；\n"
                + "- JSON字段: status(\"SAFE\"或\"FLAGGED\"), riskLevel(LOW/MEDIUM/HIGH), score(0-100数字), categories(数组), findings(数组，含 term, category, confidence(0-100), snippet, startOffset, endOffset)。\n"
                + "- 如果内容安全，返回: {\"status\":\"SAFE\",\"riskLevel\":\"LOW\",\"score\":0,\"categories\":[],\"findings\":[]}。\n"
        );
        messages.add(sys);
        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", text);
        messages.add(user);

        payload.put("messages", messages);
        payload.put("stream", false);
        payload.put("temperature", 0);
        payload.put("top_p", 1);
        payload.put("max_tokens", 512);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        } catch (Exception e) {
            throw new RuntimeException("请求构建失败", e);
        }

        String url = baseUrl + "/chat/completions";
        String body = null;
        int attempts = 3;
        long delay = 200;
        for (int i = 0; i < attempts; i++) {
            try {
                log.info("【LLM审查】发起请求，尝试次数: {}/{}", i + 1, attempts);
                body = rt.postForObject(url, entity, String.class);
                log.info("【LLM审查】请求成功，耗时: {}ms", System.currentTimeMillis() - start);
                break;
            } catch (Exception ex) {
                log.warn("【LLM审查】请求失败 (第 {} 次尝试): {}", i + 1, ex.getMessage());
                if (i == attempts - 1) {
                    log.error("【LLM审查】所有重试均失败", ex);
                    SensitiveCheckResult err = new SensitiveCheckResult();
                    err.setStatus("ERROR");
                    err.setModel("modelscope:" + modelId);
                    err.setCheckedAt(Instant.now().toString());
                    err.setMessage("LLM请求失败: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                    err.setDurationMs(System.currentTimeMillis() - start);
                    return err;
                }
                try { Thread.sleep(delay); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                delay *= 2;
            }
        }

        SensitiveCheckResult result = new SensitiveCheckResult();
        result.setModel("modelscope:" + modelId);
        result.setCheckedAt(Instant.now().toString());

        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode choices = root.path("choices");
            String content = choices.isArray() && choices.size() > 0
                    ? choices.get(0).path("message").path("content").asText()
                    : "";

            if (content == null) content = "";
            String trimmed = content.trim();
            String normalized = normalizeContent(trimmed);

            if (normalized.equalsIgnoreCase("SAFE")) {
                result.setStatus("SAFE");
                result.setRiskLevel("LOW");
                result.setScore(0.0);
                result.setCategories(new ArrayList<>());
                result.setFindings(new ArrayList<>());
            } else {
                JsonNode json = tryParseJson(normalized);
                if ((json == null || !json.isObject())) {
                    int s = normalized.indexOf('{');
                    int e = normalized.lastIndexOf('}');
                    if (s >= 0 && e > s) {
                        json = tryParseJson(normalized.substring(s, e + 1));
                    }
                }
                if (json != null && json.isObject()) {
                    fillFromJson(result, json);
                } else {
                    log.warn("【LLM审查】无法解析非结构化响应: {}", normalized);
                    result.setStatus("FLAGGED");
                    result.setRiskLevel("MEDIUM");
                    result.setScore(50.0);
                    result.setCategories(List.of("others"));
                    SensitiveCheckResult.Finding f = new SensitiveCheckResult.Finding();
                    f.setTerm("content");
                    f.setCategory("others");
                    f.setConfidence(0.5);
                    String sn = normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
                    f.setSnippet(sn);
                    result.setFindings(List.of(f));
                }
            }
        } catch (Exception e) {
            log.error("【LLM审查】结果解析失败: body=" + body, e);
            result.setStatus("ERROR");
            result.setMessage("解析失败");
        }

        result.setDurationMs(System.currentTimeMillis() - start);
        try {
            JsonNode usage = objectMapper.readTree(body == null ? "{}" : body).path("usage");
            SensitiveCheckResult.TokenUsage tu = new SensitiveCheckResult.TokenUsage();
            tu.setPromptTokens(usage.path("prompt_tokens").isNumber() ? usage.path("prompt_tokens").asInt() : null);
            tu.setCompletionTokens(usage.path("completion_tokens").isNumber() ? usage.path("completion_tokens").asInt() : null);
            tu.setTotalTokens(usage.path("total_tokens").isNumber() ? usage.path("total_tokens").asInt() : null);
            result.setTokenUsage(tu);
        } catch (Exception ignored) {}

        return result;
    }

    private List<String> splitText(String text, int size) {
        List<String> parts = new ArrayList<>();
        if (text == null) return parts;
        int len = text.length();
        for (int i = 0; i < len; i += size) {
            parts.add(text.substring(i, Math.min(len, i + size)));
        }
        return parts;
    }

}
