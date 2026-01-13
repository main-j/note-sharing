package com.project.login.service.conversationService;

import com.project.login.mapper.ConversationRelationMapper;
import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.ConversationDO;
import com.project.login.model.dataobject.ConversationRelationDO;
import com.project.login.model.vo.ConversationListItemVO;
import com.project.login.model.vo.ConversationListVO;
import com.project.login.model.vo.ConversationVO;
import com.project.login.repository.ConversationRepository;
import com.project.login.service.userFollow.UserFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRelationMapper relationMapper;
    private final ConversationRepository conversationRepository;
    private final UserMapper userMapper;
    private final UserFollowService userFollowService;

    /** 发送消息，返回 conversationId */
    public String sendMessage(Long senderId, Long receiverId, String content) {
        if (senderId.equals(receiverId)) return null;

        // 校验用户
        if (userMapper.selectById(senderId) == null || userMapper.selectById(receiverId) == null) {
            return null;
        }
        if(!userFollowService.isMutualFollow(senderId,receiverId)){
            return null;
        }
        
        return sendMessageInternal(senderId, receiverId, content);
    }
    
    /** 管理员发送消息（不需要互相关注），返回 conversationId */
    public String sendAdminMessage(Long receiverId, String content) {
        // 通过管理员邮箱查找真实的管理员用户ID
        // 使用admin-whitelist.json中的第一个管理员邮箱
        String adminEmail = "chennzh5@mail2.sysu.edu.cn";
        com.project.login.model.entity.UserEntity adminUser = userMapper.selectByEmail(adminEmail);
        
        Long adminId;
        if (adminUser != null && adminUser.getId() != null) {
            adminId = adminUser.getId();
            log.info("【管理员私信】使用管理员用户ID: {}, 用户名: {}", adminId, adminUser.getUsername());
        } else {
            // 如果找不到管理员用户，尝试通过用户名查找（备用方案）
            // 尝试常见的管理员用户名
            String[] adminUsernames = {"admin", "administrator", "管理员"};
            adminId = null;
            for (String username : adminUsernames) {
                com.project.login.model.entity.UserEntity adminByUsername = userMapper.selectByUsername(username);
                if (adminByUsername != null && adminByUsername.getId() != null && "Admin".equals(adminByUsername.getRole())) {
                    adminId = adminByUsername.getId();
                    log.info("【管理员私信】通过用户名找到管理员用户ID: {}, 用户名: {}", adminId, username);
                    break;
                }
            }
            
            if (adminId == null) {
                log.error("【管理员私信】无法找到管理员用户（邮箱: {}, 尝试的用户名: {}），私信可能无法正常显示", 
                    adminEmail, String.join(", ", adminUsernames));
                // 如果找不到管理员，返回null而不是使用0，避免创建无效的会话
                return null;
            }
        }
        
        // 校验接收者
        if (userMapper.selectById(receiverId) == null) {
            log.warn("【管理员私信】接收者用户不存在: {}", receiverId);
            return null;
        }
        
        return sendMessageInternal(adminId, receiverId, content);
    }
    
    /** 内部发送消息方法（不检查互相关注） */
    private String sendMessageInternal(Long senderId, Long receiverId, String content) {
        // 保证 user1 < user2
        Long user1 = Math.min(senderId, receiverId);
        Long user2 = Math.max(senderId, receiverId);

        String conversationId = user1 + "TO" + user2;

        // MySQL：会话关系（只负责“是否存在”）
        ConversationRelationDO relation = relationMapper.selectByUserPair(user1, user2);
        if (relation == null) {
            relation = ConversationRelationDO.builder()
                    .user1Id(user1)
                    .user2Id(user2)
                    .conversationId(conversationId)
                    .createTime(java.time.LocalDateTime.now())
                    .build();
            relationMapper.insert(relation);
        }

        // MongoDB：只在不存在时创建会话 document
        conversationRepository.findByConversationId(conversationId)
                .orElseGet(() -> conversationRepository.save(
                        ConversationDO.builder()
                                .conversationId(conversationId)
                                .participants(Arrays.asList(user1, user2))
                                .messages(new ArrayList<>())
                                .lastTime(new Date())
                                .build()
                ));

        // 构建消息
        Date now = new Date();
        ConversationDO.Message message = ConversationDO.Message.builder()
                .senderId(senderId)
                .content(content)
                .createdAt(now)
                .build();

        // MongoDB 原子追加
        conversationRepository.appendMessage(
                conversationId,
                message,
                java.time.LocalDateTime.now()
        );

        return conversationId;
    }


    /** 获取用户会话列表（返回 ConversationListVO） */
    public ConversationListVO getConversationsList(Long userId) {
        List<ConversationRelationDO> relations = relationMapper.selectByUserId(userId);
        List<ConversationListItemVO> list = new ArrayList<>();

        for (ConversationRelationDO relation : relations) {
            String convId = relation.getConversationId();
            Long peerId = relation.getUser1Id().equals(userId) ? relation.getUser2Id() : relation.getUser1Id();

            conversationRepository.findByConversationId(convId)
                    .ifPresent(conv -> {
                        String lastMsg = (conv.getMessages() != null && !conv.getMessages().isEmpty())
                                ? conv.getMessages().get(conv.getMessages().size() - 1).getContent()
                                : null;
                        Date lastTime = conv.getLastTime();
                        list.add(ConversationListItemVO.builder()
                                .peerId(peerId)
                                .conversationId(convId)
                                .lastMessage(lastMsg)
                                .lastTime(lastTime)
                                .build());
                    });
        }

        return ConversationListVO.builder()
                .userId(userId)
                .conversations(list)
                .build();
    }

    /** 获取单条会话完整信息 */
    public ConversationVO getConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId)
                .map(conv -> ConversationVO.builder()
                        .userId(null)
                        .conversationId(conv.getConversationId())
                        .participants(conv.getParticipants())
                        .messages(conv.getMessages())
                        .lastTime(conv.getLastTime())
                        .build())
                .orElse(null);
    }

    /** 删除消息 */
    public boolean deleteMessage(String conversationId, int messageIndex) {
        Optional<ConversationDO> opt = conversationRepository.findByConversationId(conversationId);
        if (opt.isEmpty()) return false;

        ConversationDO conversationDO = opt.get();
        List<ConversationDO.Message> messages = conversationDO.getMessages();
        if (messages == null || messageIndex < 0 || messageIndex >= messages.size()) return false;

        messages.remove(messageIndex);

        if (!messages.isEmpty()) {
            conversationDO.setLastTime(messages.get(messages.size() - 1).getCreatedAt());
        } else {
            conversationDO.setLastTime(null);
        }

        conversationRepository.save(conversationDO);
        return true;
    }
}
