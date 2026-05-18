package com.project.login.service.remark;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemarkScheduledTasks {

    private final RemarkService remarkService;

    // 学 NoteStats，每 5 分钟把 Redis stats Hash 推送到 MQ；TTL 7 天 >> 周期，避免数据丢失。
    @Scheduled(cron = "0 */5 * * * *")
    public void flushRedisLikeCountToMQ() {
        log.info("Flushing remark like count to MQ");
        remarkService.flushLikeCountToMQ();
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void flushRedisLikeUsersToMQ() {
        log.info("Flushing remark like users to MQ");
        remarkService.flushLikeUsersToMQ();
    }
}