package com.project.login.service.sensitive;

import com.project.login.model.vo.SensitiveCheckResult;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class NoteModerationConsumer {

    @Resource
    private SensitiveWordService sensitiveWordService;

    @Resource
    private ModerationService moderationService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "note.audit.queue")
    public void onMessage(String payload, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        long start = System.currentTimeMillis();
        log.info("【敏感词审查】收到队列消息: payload={}", payload);
        try {
            Long noteId = Long.valueOf(payload);
            SensitiveCheckResult r = sensitiveWordService.checkNote(noteId, true);
            
            log.info("【敏感词审查】审查完成: noteId={}, status={}, risk={}, score={}, duration={}ms", 
                    noteId, r.getStatus(), r.getRiskLevel(), r.getScore(), System.currentTimeMillis() - start);

            log.info("【敏感词审查】正在保存结果: noteId={}", noteId);
            moderationService.saveResult(r);
            log.info("【敏感词审查】结果保存成功: noteId={}", noteId);
            
            if ("FLAGGED".equalsIgnoreCase(r.getStatus())) {
                log.warn("【敏感词审查】发现违规笔记: noteId={}, categories={}", noteId, r.getCategories());
                rabbitTemplate.convertAndSend("note.moderation.alert.queue", String.valueOf(noteId));
            }
            
            // 手动确认消息
            log.info("【敏感词审查】执行ACK: tag={}", tag);
            channel.basicAck(tag, false);
            log.info("【敏感词审查】ACK完成: tag={}", tag);
        } catch (Exception e) {
            log.error("【敏感词审查】处理异常: payload=" + payload, e);
            try {
                // 异常情况下拒绝消息，不重回队列，防止死循环刷日志/LLM
                channel.basicNack(tag, false, false);
            } catch (IOException ex) {
                log.error("消息确认失败", ex);
            }
        }
    }
}
