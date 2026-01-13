package com.project.login.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 笔记审查结果VO，包含笔记内容和深度检测结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteReviewVO {
    private Long noteId;
    private String noteTitle;
    private String noteContent;        // 笔记的文本内容
    private String fileType;
    
    // 深度检测结果
    private Boolean hasSensitiveWords;
    private Integer hitCount;
    private List<String> uniqueHits;   // 去重后的敏感词列表
    private List<Map<String, Object>> findings;  // 检测发现项，包含位置信息
}
