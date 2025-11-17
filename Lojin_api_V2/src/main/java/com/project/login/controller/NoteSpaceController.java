package com.project.login.controller;

import com.project.login.model.dto.request.notespace.NoteSpaceCreateRequest;
import com.project.login.model.dto.request.notespace.NoteSpaceUpdateRequest;
import com.project.login.model.dto.request.notespace.NoteSpaceDeleteRequest;
import com.project.login.service.noting.NoteSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Map;

@Tag(name = "Note Spaces", description = "Manage note spaces")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/noting/spaces")
public class NoteSpaceController {

    private final NoteSpaceService notespaceService;

    @Operation(summary = "Create note space")
    @PostMapping
    public ResponseEntity<?> createSpace(@Valid @RequestBody NoteSpaceCreateRequest request) {
        return ResponseEntity.ok(notespaceService.createSpace(request));
    }

    @Operation(summary = "Rename space")
    @PutMapping
    public ResponseEntity<?> updateSpace(
            @Valid @RequestBody NoteSpaceUpdateRequest request
    ) {
        return ResponseEntity.ok(notespaceService.renameSpace(request));
    }

    @Operation(summary = "Delete space")
    @DeleteMapping
    public ResponseEntity<?> deleteSpace(@Valid @RequestBody NoteSpaceDeleteRequest request) {
        notespaceService.deleteSpace(request);
        return ResponseEntity.ok(Map.of("message", "Space deleted"));
    }
}

