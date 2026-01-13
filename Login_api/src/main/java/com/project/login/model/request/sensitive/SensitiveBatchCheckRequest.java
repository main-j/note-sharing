package com.project.login.model.request.sensitive;

import lombok.Data;
import java.util.List;

@Data
public class SensitiveBatchCheckRequest {
    private List<Long> noteIds;
    private Integer concurrency;  // 并发数
    private Boolean full;         // 是否全文检查
}
