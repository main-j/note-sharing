package com.project.login.model.request.note;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteFavoriteRequest {
    @NotNull(message = "Note ID is required")
    private Long noteId;
    
    @NotNull(message = "User ID is required")
    private Long userId;
}
