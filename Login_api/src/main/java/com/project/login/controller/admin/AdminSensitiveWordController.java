package com.project.login.controller.admin;

import com.project.login.model.request.sensitive.SensitiveBatchCheckRequest;
import com.project.login.model.request.sensitive.SensitiveTextCheckRequest;
import com.project.login.model.response.StandardResponse;
import com.project.login.model.vo.SensitiveCheckResult;
import com.project.login.service.sensitive.SensitiveWordService;
import com.project.login.service.sensitive.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

import com.project.login.model.request.note.NoteDeleteRequest;
import com.project.login.service.noting.NoteService;
import com.project.login.convert.NoteConvert;
import com.project.login.mapper.NoteModerationMapper;
import com.project.login.model.dataobject.NoteModerationDO;
import java.util.Map;

@Tag(name = "Admin Sensitive", description = "Admin sensitive word checking")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/sensitive")
public class AdminSensitiveWordController {

    private final SensitiveWordService sensitiveWordService;
    private final NoteModerationMapper noteModerationMapper;
    private final NoteService noteService;
    private final NoteConvert noteConvert;
    private final ModerationService moderationService;

    @Value("${sensitive.scan.concurrency:4}")
    private int defaultConcurrency;

    @Operation(summary = "Get pending flagged notes")
    @GetMapping("/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<StandardResponse<List<NoteModerationDO>>> getPending() {
        return ResponseEntity.ok(StandardResponse.success(noteModerationMapper.selectPendingFlagged()));
    }

    @Operation(summary = "Handle moderation result")
    @PostMapping("/handle/{id}")
    @Transactional
    public ResponseEntity<StandardResponse<String>> handle(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        NoteModerationDO rec = noteModerationMapper.selectById(id);
        if (rec == null) return ResponseEntity.ok(StandardResponse.error("记录不存在"));

        String action = body.getOrDefault("action", "IGNORE"); // BAN or IGNORE
        String comment = body.getOrDefault("comment", "");

        if ("BAN".equalsIgnoreCase(action)) {
            NoteDeleteRequest req = new NoteDeleteRequest();
            req.setNoteId(rec.getNoteId());
            try {
                noteService.deleteNote(noteConvert.toDeleteDTO(req));
            } catch (Exception e) {
                // ignore if already deleted
            }
        }

        rec.setIsHandled(true);
        rec.setAdminComment(action + ": " + comment);
        noteModerationMapper.updateHandled(rec);

        return ResponseEntity.ok(StandardResponse.success("已处理"));
    }

    @Operation(summary = "Check single note")
    @PostMapping("/check/{noteId}")
    @Transactional(readOnly = true)
    public ResponseEntity<StandardResponse<SensitiveCheckResult>> checkNote(@PathVariable("noteId") Long noteId,
                                                                            @RequestParam(value = "full", required = false, defaultValue = "false") boolean full,
                                                                            @RequestParam(value = "persist", required = false, defaultValue = "false") boolean persist) {
        SensitiveCheckResult r = sensitiveWordService.checkNote(noteId, full);
        if (persist) {
            moderationService.saveResult(r);
        }
        return ResponseEntity.ok(StandardResponse.success(r));
    }

    @Operation(summary = "Batch check notes")
    @PostMapping("/check/batch")
    @Transactional(readOnly = true)
    public ResponseEntity<StandardResponse<List<SensitiveCheckResult>>> batch(@RequestBody SensitiveBatchCheckRequest req,
                                                                             @RequestParam(value = "persist", required = false, defaultValue = "false") boolean persist) {
        List<SensitiveCheckResult> list = new ArrayList<>();
        if (req.getNoteIds() != null) {
            int requested = req.getConcurrency() == null || req.getConcurrency() <= 0 ? 3 : req.getConcurrency();
            int c = Math.min(defaultConcurrency, requested);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(c);
            List<java.util.concurrent.Future<SensitiveCheckResult>> futures = new ArrayList<>();
            for (Long id : req.getNoteIds()) {
                futures.add(pool.submit(() -> {
                    try {
                        SensitiveCheckResult rr = sensitiveWordService.checkNote(id, Boolean.TRUE.equals(req.getFull()));
                        if (persist) {
                            moderationService.saveResult(rr);
                        }
                        return rr;
                    } catch (RuntimeException e) {
                        SensitiveCheckResult r = new SensitiveCheckResult();
                        SensitiveCheckResult.NoteMeta meta = new SensitiveCheckResult.NoteMeta();
                        meta.setNoteId(id);
                        r.setNoteMeta(meta);
                        r.setStatus("ERROR");
                        r.setMessage(e.getMessage());
                        return r;
                    }
                }));
            }
            pool.shutdown();
            for (java.util.concurrent.Future<SensitiveCheckResult> f : futures) {
                try {
                    list.add(f.get());
                } catch (Exception e) {
                    SensitiveCheckResult r = new SensitiveCheckResult();
                    r.setStatus("ERROR");
                    r.setMessage("并发执行失败");
                    list.add(r);
                }
            }
        }
        return ResponseEntity.ok(StandardResponse.success(list));
    }

    @Operation(summary = "Check plain text")
    @PostMapping("/check/text")
    @Transactional(readOnly = true)
    public ResponseEntity<StandardResponse<SensitiveCheckResult>> checkText(@RequestBody SensitiveTextCheckRequest req) {
        SensitiveCheckResult r = sensitiveWordService.checkText(req.getText());
        return ResponseEntity.ok(StandardResponse.success(r));
    }
}
