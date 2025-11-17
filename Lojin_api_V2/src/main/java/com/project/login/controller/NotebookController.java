package com.project.login.controller;

import com.project.login.model.dto.request.notebook.*;
import com.project.login.service.noting.NotebookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Map;

@Tag(name = "Notebooks", description = "Manage notebooks")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/noting/notebooks")
public class NotebookController {

    private final NotebookService notebookService;

    @Operation(summary = "Create notebook")
    @PostMapping
    public ResponseEntity<?> createNotebook(@Valid @RequestBody NotebookCreateRequest request) {
        return ResponseEntity.ok(notebookService.createNotebook(request));
    }

    @Operation(summary = "Update notebook")
    @PutMapping
    public ResponseEntity<?> updateNotebook(@Valid @RequestBody NotebookUpdateRequest request) {
        return ResponseEntity.ok(notebookService.updateNotebook(request));
    }

    @Operation(summary = "Delete notebook")
    @DeleteMapping
    public ResponseEntity<?> deleteNotebook(@Valid @RequestBody NotebookDeleteRequest request) {
        notebookService.deleteNotebook(request);
        return ResponseEntity.ok(Map.of("message", "Notebook deleted"));
    }

    @Operation(summary = "Move note to another notebook")
    @PutMapping("/move-notebook")
    public ResponseEntity<?> moveNotebook(@Valid @RequestBody NotebookMoveRequest request) {
        return ResponseEntity.ok(notebookService.moveNotebook(request));
    }

    @Operation(summary = "Get notebooks by note space")
    @PostMapping("/by-space")
    public ResponseEntity<?> getNotebooksBySpace(
            @Valid @RequestBody NotebookListBySpaceRequest request
    ) {
        return ResponseEntity.ok(notebookService.getNotebooksBySpace(request));
    }
}
