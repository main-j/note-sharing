package com.project.login.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.login.model.dto.request.note.*;
import com.project.login.service.noting.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static java.lang.IO.println;

@Tag(name = "Notes", description = "Manage notes")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/noting/notes")
public class NoteController {

    private final NoteService noteService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create note with file and meta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success")
    })
    public ResponseEntity<?> createNote(

            @Parameter(
                    description = "Note metadata JSON string",
                    required = true,
                    content = @Content(mediaType = "application/json")
            )
            @RequestPart("meta") String metaJson,

            @Parameter(
                    description = "File to upload",
                    required = true,
                    content = @Content(mediaType = "application/octet-stream")
            )
            @RequestPart("file") MultipartFile file
    ) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();
        NoteMeta meta = objectMapper.readValue(metaJson, NoteMeta.class);

        NoteCreateRequest request = new NoteCreateRequest();
        request.setMeta(meta);
        request.setFile(file);

        return ResponseEntity.ok(noteService.createNote(request));
    }


    @Operation(summary = "Update note")
    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateNote(@RequestPart("meta") String metaJson,
                                        @RequestPart(value = "file", required = false) MultipartFile file
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NoteMeta meta = mapper.readValue(metaJson, NoteMeta.class);

        NoteUpdateRequest request = new NoteUpdateRequest();
        request.setMeta(meta);
        request.setFile(file);
        return ResponseEntity.ok(noteService.updateNote(request));
    }

    @Operation(summary = "Delete note")
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteNote(@Valid @RequestBody NoteDeleteRequest request) {
        noteService.deleteNote(request);
        return ResponseEntity.ok(Map.of("message", "Note deleted"));
    }

    @Operation(summary = "Move note to another notebook")
    @PostMapping("/move")
    public ResponseEntity<?> moveNote(@Valid @RequestBody NoteMoveRequest request) {
        return ResponseEntity.ok(noteService.moveNote(request));
    }

    @Operation(summary = "Get notes by notebook")
    @PostMapping("/by-notebook")
    public ResponseEntity<?> getNotesByNotebook(@Valid @RequestBody NoteListByNotebookRequest request) {
        return ResponseEntity.ok(noteService.getNotesByNotebook(request));
    }

    @Operation(summary = "Upload attachment")
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestPart("meta") String metaJson,
                                        @RequestPart(value = "file", required = false) MultipartFile file
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NoteMeta meta = mapper.readValue(metaJson, NoteMeta.class);

        NoteUploadFileRequest request = new NoteUploadFileRequest();
        request.setMeta(meta);
        request.setFile(file);
        return ResponseEntity.ok(noteService.uploadFile(request));
    }

    @Operation(summary = "Get file access URL")
    @PostMapping("/files/url")
    public ResponseEntity<?> getFileUrl(@Valid @RequestBody NoteFileUrlRequest request) {
        return ResponseEntity.ok(noteService.getFileUrl(request));
    }
}
