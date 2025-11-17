package com.project.login.model.dto.request.notespace;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteSpaceDeleteRequest {

    @NotNull(message = "Space ID cannot be null")
    private Long id;
}
