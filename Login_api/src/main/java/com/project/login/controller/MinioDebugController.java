package com.project.login.controller;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.project.login.model.response.StandardResponse;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MinIO 文件管理控制器（仅用于调试和文件管理）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/minio")
@RequiredArgsConstructor
public class MinioDebugController {

    private final MinioClient minioClient;
    private final String bucket = "notesharing";

    /**
     * 列出 MinIO bucket 中的所有文件
     */
    @GetMapping("/files/list")
    public StandardResponse<List<Map<String, Object>>> listMinioFiles() {
        try {
            List<Map<String, Object>> fileList = new ArrayList<>();
            int fileCount = 0;
            long totalSize = 0;

            // 列出 bucket 中的所有对象
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    Map<String, Object> fileInfo = Map.of(
                            "objectName", item.objectName(),
                            "size", item.size(),
                            "lastModified", item.lastModified() != null ? 
                                    item.lastModified().format(DateTimeFormatter.ISO_DATE_TIME) : "N/A",
                            "isDir", item.isDir()
                    );
                    fileList.add(fileInfo);
                    fileCount++;
                    totalSize += item.size();
                }
            }

            log.info("Successfully listed {} files from MinIO bucket '{}', total size: {}", 
                    fileCount, bucket, totalSize);

            return StandardResponse.success(fileList);
        } catch (Exception e) {
            log.error("Failed to list files from MinIO bucket '{}'", bucket, e);
            return StandardResponse.error("无法列出 MinIO 文件: " + e.getMessage());
        }
    }

}
