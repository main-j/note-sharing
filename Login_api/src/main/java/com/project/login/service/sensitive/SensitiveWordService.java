package com.project.login.service.sensitive;

import com.project.login.model.vo.SensitiveCheckResult;

public interface SensitiveWordService {
    /**
     * 检查笔记（全文模式）
     */
    SensitiveCheckResult checkNote(Long noteId);
    
    /**
     * 检查笔记（全文模式，兼容旧接口）
     * @param noteId 笔记ID
     * @param full 已废弃，统一使用全文检查
     */
    SensitiveCheckResult checkNote(Long noteId, boolean full);
    
    /**
     * 检查纯文本
     */
    SensitiveCheckResult checkText(String text);
}
