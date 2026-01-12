package com.project.login.service.conversationService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatUnreadService {

    private final StringRedisTemplate redisTemplate;

    private final SimpUserRegistry simpUserRegistry;

    private static final String KEY_PREFIX = "chat:unread:";


    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    /** 未读 +1 */
    public void incr(Long userId, String conversationId) {
        redisTemplate.opsForHash()
                .increment(key(userId), conversationId, 1);
    }

    /** 清空某个会话的未读 */
    public void clear(Long userId, String conversationId) {
        redisTemplate.opsForHash()
                .delete(key(userId), conversationId);
    }

    /** 获取某会话未读数 */
    public int getUnread(Long userId, String conversationId) {
        Object val = redisTemplate.opsForHash()
                .get(key(userId), conversationId);
        return val == null ? 0 : Integer.parseInt(val.toString());
    }

    /** 获取全部未读（用于会话列表） */
    public Map<Object, Object> getAllUnread(Long userId) {
        return redisTemplate.opsForHash().entries(key(userId));
    }


    public boolean isUserOnline(Long userId, String conversationId) {
        SimpUser user = simpUserRegistry.getUser(userId.toString());
        if (user == null || user.getSessions().isEmpty()) {
            return false; // 用户完全离线
        }

        String queueDestination = "/user/queue/messages." + conversationId;

        // 遍历用户所有会话
        for (var session : user.getSessions()) {
            // getSubscriptions 返回 Set<SimpSubscription>
            for (var sub : session.getSubscriptions()) {
                if (queueDestination.equals(sub.getDestination())) {
                    return true; // 找到订阅该会话队列
                }
            }
        }

        return false; // 没有订阅该会话队列
    }
    public int getUnreadTotal(Long userId) {
        Map<Object, Object> allUnread = redisTemplate.opsForHash().entries(key(userId));
        if (allUnread == null || allUnread.isEmpty()) {
            return 0;
        }
        return allUnread.values().stream()
                .mapToInt(val -> {
                    try {
                        return Integer.parseInt(val.toString());
                    } catch (NumberFormatException e) {
                        return 0; // 避免 Redis 数据异常导致报错
                    }
                })
                .sum();
    }



}


