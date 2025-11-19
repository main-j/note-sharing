package com.project.login.model.dto.request.note;


import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ImageUrlRequest {
    private MultipartFile file;
}
