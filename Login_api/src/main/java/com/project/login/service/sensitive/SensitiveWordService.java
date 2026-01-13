package com.project.login.service.sensitive;

import com.project.login.model.vo.SensitiveCheckResult;

public interface SensitiveWordService {
    /**
     * 检查笔记（快速模式，使用摘要）
     */
    SensitiveCheckResult checkNote(Long noteId);
    
    /**
     * 检查笔记
     * @param noteId 笔记ID
     * @param full true=全文检查，false=摘要检查
     */
    SensitiveCheckResult checkNote(Long noteId, boolean full);
    
    /**
     * 检查纯文本
     */
    SensitiveCheckResult checkText(String text);
}
