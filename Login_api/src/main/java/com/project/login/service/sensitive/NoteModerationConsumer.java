package com.project.login.service.sensitive;

import com.project.login.model.vo.SensitiveCheckResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoteModerationConsumer {

    @Resource
    private SensitiveWordService sensitiveWordService;

    @Resource
    private ModerationService moderationService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "note.audit.queue", containerFactory = "auditListenerFactory")
    public void onMessage(String payload) {
        long start = System.currentTimeMillis();
        log.info("【敏感词审查】收到队列消息: payload={}", payload);
        try {
            Long noteId = Long.valueOf(payload);
            SensitiveCheckResult r = sensitiveWordService.checkNote(noteId, true);
            
            log.info("【敏感词审查】审查完成: noteId={}, status={}, risk={}, score={}, duration={}ms", 
                    noteId, r.getStatus(), r.getRiskLevel(), r.getScore(), System.currentTimeMillis() - start);

            moderationService.saveResult(r);
            
            if ("FLAGGED".equalsIgnoreCase(r.getStatus())) {
                log.warn("【敏感词审查】发现违规笔记: noteId={}, categories={}", noteId, r.getCategories());
                rabbitTemplate.convertAndSend("note.moderation.alert.queue", String.valueOf(noteId));
            }
        } catch (NumberFormatException e) {
            log.error("【敏感词审查】消息格式错误，无法解析 noteId: {}", payload);
        } catch (Exception e) {
            log.error("【敏感词审查】处理异常: payload=" + payload, e);
        }
    }
}
