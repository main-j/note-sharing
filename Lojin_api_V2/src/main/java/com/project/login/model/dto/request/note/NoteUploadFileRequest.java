package com.project.login.model.dto.request.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class NoteUploadFileRequest {

    @NotNull(message = "meta cannot be null")
    private NoteMeta meta;

    // 上传的md文件内容
    @NotNull(message = "file cannot be null")
    private MultipartFile file;
}
