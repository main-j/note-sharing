package com.project.login.model.request.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteSearchRequest {
    @NotBlank
    private String keyword;
    @NotNull
    private Long userId;
}
