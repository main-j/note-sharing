package com.project.login.model.dto.request.notespace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteSpaceCreateRequest {

    @NotBlank(message = "Space name cannot be blank")
    private String name;

    @NotNull(message = "User ID cannot be null")
    private Long userId;

    @NotNull(message = "Tag cannot be null")
    private String tag;
}

