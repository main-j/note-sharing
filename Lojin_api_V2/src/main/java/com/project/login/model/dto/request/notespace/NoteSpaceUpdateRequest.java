package com.project.login.model.dto.request.notespace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data
public class NoteSpaceUpdateRequest {

    @NotNull(message = "Space ID cannot be null")
    private Long id;

    @NotBlank(message = "New name cannot be empty")
    private String name;

    @NotBlank(message = "标签不能为空")
    private String tag;
}

