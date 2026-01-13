package com.project.login.service.notification;

import com.project.login.mapper.NoteMapper;
import com.project.login.mapper.NotebookMapper;
import com.project.login.mapper.NoteSpaceMapper;
import com.project.login.mapper.UserFollowMapper;
import com.project.login.mapper.UserMapper;
import com.project.login.model.dataobject.*;
import com.project.login.model.vo.NotificationVO;
import com.project.login.repository.NotificationRepository;
import com.project.login.repository.QuestionRepository;
import com.project.login.repository.RemarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;
    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper noteSpaceMapper;
    private final UserFollowMapper userFollowMapper;
    private final QuestionRepository questionRepository;
    private final RemarkRepository remarkRepository;

    // ================== 公共方法 ==================

    public List<NotificationVO> listLatestForUser(Long userId) {
        List<NotificationDO> list = notificationRepository.findTop50ByReceiverIdOrderByCreatedAtDesc(userId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    public long getUnreadTotal(Long userId) {
        return notificationRepository.countByReceiverIdAndReadFalse(userId);
    }

    public void markAsRead(String notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markAllAsRead(Long userId) {
        List<NotificationDO> list = notificationRepository.findByReceiverIdAndReadFalse(userId);
        if (list.isEmpty()) {
            return;
        }
        list.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(list);
    }

    // ================== 笔记相关 ==================

    /** 喜欢/赞同我的笔记 */
    public void createNoteLikeNotification(Long actorId, Long noteId) {
        Long ownerId = resolveNoteOwnerId(noteId);
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveSimpleNotification(ownerId, actorId, NotificationType.LIKE_NOTE, "NOTE", String.valueOf(noteId));
    }

    /** 收藏我的笔记 */
    public void createNoteFavoriteNotification(Long actorId, Long noteId) {
        Long ownerId = resolveNoteOwnerId(noteId);
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveSimpleNotification(ownerId, actorId, NotificationType.FAVORITE_NOTE, "NOTE", String.valueOf(noteId));
    }

    /** 评论我的笔记（一级评论） */
    public void createNoteCommentNotification(Long actorId, Long noteId) {
        Long ownerId = resolveNoteOwnerId(noteId);
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveSimpleNotification(ownerId, actorId, NotificationType.NOTE_COMMENT, "NOTE", String.valueOf(noteId));
    }

    /** 评论我的评论（回复评论） */
    public void createNoteReplyCommentNotification(String repliedRemarkId, Long actorId) {
        if (repliedRemarkId == null) return;
        remarkRepository.findById(repliedRemarkId).ifPresent(replied -> {
            Long receiverId = replied.getUserId();
            if (receiverId == null || Objects.equals(receiverId, actorId)) return;
            Long noteId = replied.getNoteId();
            saveSimpleNotification(receiverId, actorId, NotificationType.NOTE_COMMENT_REPLY, "NOTE", String.valueOf(noteId));
        });
    }

    /** 笔记审查通过 */
    public void createNoteModerationApprovedNotification(Long actorId, Long noteId, String noteTitle) {
        Long ownerId = resolveNoteOwnerId(noteId);
        if (ownerId == null) return;
        NotificationDO entity = buildBase(ownerId, actorId, NotificationType.NOTE_MODERATION_APPROVED, "NOTE", String.valueOf(noteId));
        entity.setMessage(String.format("您的笔记《%s》已通过管理员审查并成功发布。", noteTitle));
        try {
            notificationRepository.save(entity);
        } catch (Exception e) {
            log.warn("保存笔记审查通过通知失败 noteId={}", noteId, e);
        }
    }

    /** 笔记审查未通过 */
    public void createNoteModerationRejectedNotification(Long actorId, Long noteId, String noteTitle, String reason) {
        Long ownerId = resolveNoteOwnerId(noteId);
        if (ownerId == null) return;
        NotificationDO entity = buildBase(ownerId, actorId, NotificationType.NOTE_MODERATION_REJECTED, "NOTE", String.valueOf(noteId));
        String message = String.format("您的笔记《%s》未通过管理员审查。原因：%s", 
            noteTitle, reason != null && !reason.trim().isEmpty() ? reason : "内容不符合平台规范");
        entity.setMessage(message);
        try {
            notificationRepository.save(entity);
        } catch (Exception e) {
            log.warn("保存笔记审查未通过通知失败 noteId={}", noteId, e);
        }
    }

    /** 我关注的人发布了笔记 */
    public void createNotePublishNotifications(Long noteId) {
        Long ownerId = resolveNoteOwnerId(noteId);
        if (ownerId == null) return;
        List<UserFollowDO> followers = userFollowMapper.selectFollowers(ownerId);
        if (followers == null || followers.isEmpty()) return;
        for (UserFollowDO follower : followers) {
            Long followerId = follower.getFollowerId();
            if (Objects.equals(followerId, ownerId)) continue;
            saveSimpleNotification(followerId, ownerId, NotificationType.FOLLOWEE_PUBLISH_NOTE, "NOTE", String.valueOf(noteId));
        }
    }

    private Long resolveNoteOwnerId(Long noteId) {
        if (noteId == null) return null;
        NoteDO note = noteMapper.selectById(noteId);
        if (note == null) return null;
        Long notebookId = note.getNotebookId();
        if (notebookId == null) return null;
        NotebookDO notebook = notebookMapper.selectById(notebookId);
        if (notebook == null) return null;
        Long spaceId = notebook.getSpaceId();
        if (spaceId == null) return null;
        NoteSpaceDO space = noteSpaceMapper.selectById(spaceId);
        return space != null ? space.getUserId() : null;
    }

    // ================== 问答相关 ==================

    public void createQuestionLikeNotification(Long actorId, String questionId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        Long ownerId = q.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveSimpleNotification(ownerId, actorId, NotificationType.LIKE_QUESTION, "QUESTION", questionId);
    }

    /** 回答了我的问题 */
    public void createAnswerNotification(Long actorId, String questionId, Long answerId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        Long ownerId = q.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.ANSWER_QUESTION, questionId, answerId, null, null);
    }

    public void createAnswerLikeNotification(Long actorId, String questionId, Long answerId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        QuestionDO.AnswerDO answer = q.getAnswers().stream()
                .filter(a -> a.getAnswerId().equals(answerId))
                .findFirst().orElse(null);
        if (answer == null) return;
        Long ownerId = answer.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.LIKE_ANSWER, questionId, answerId, null, null);
    }

    public void createQuestionCommentLikeNotification(Long actorId, String questionId, Long answerId, Long commentId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        QuestionDO.CommentDO comment = q.getAnswers().stream()
                .filter(a -> a.getAnswerId().equals(answerId))
                .flatMap(a -> a.getComments().stream())
                .filter(c -> c.getCommentId().equals(commentId))
                .findFirst().orElse(null);
        if (comment == null) return;
        Long ownerId = comment.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.LIKE_QA_COMMENT, questionId, answerId, commentId, null);
    }

    public void createQuestionReplyLikeNotification(Long actorId, String questionId, Long answerId, Long commentId, Long replyId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        QuestionDO.ReplyDO reply = q.getAnswers().stream()
                .filter(a -> a.getAnswerId().equals(answerId))
                .flatMap(a -> a.getComments().stream())
                .filter(c -> c.getCommentId().equals(commentId))
                .flatMap(c -> c.getReplies().stream())
                .filter(r -> r.getReplyId().equals(replyId))
                .findFirst().orElse(null);
        if (reply == null) return;
        Long ownerId = reply.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.LIKE_QA_REPLY, questionId, answerId, commentId, replyId);
    }

    public void createQuestionFavoriteNotification(Long actorId, String questionId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        Long ownerId = q.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.FAVORITE_QUESTION, questionId, null, null, null);
    }

    public void createQuestionCommentNotification(Long actorId, String questionId, Long answerId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        QuestionDO.AnswerDO answer = q.getAnswers().stream()
                .filter(a -> a.getAnswerId().equals(answerId))
                .findFirst().orElse(null);
        if (answer == null) return;
        Long ownerId = answer.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.QA_COMMENT, questionId, answerId, null, null);
    }

    public void createQuestionReplyNotification(Long actorId, String questionId, Long answerId, Long commentId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        QuestionDO.CommentDO comment = q.getAnswers().stream()
                .filter(a -> a.getAnswerId().equals(answerId))
                .flatMap(a -> a.getComments().stream())
                .filter(c -> c.getCommentId().equals(commentId))
                .findFirst().orElse(null);
        if (comment == null) return;
        Long ownerId = comment.getAuthorId();
        if (ownerId == null || Objects.equals(ownerId, actorId)) return;
        saveQuestionNotification(ownerId, actorId, NotificationType.QA_REPLY, questionId, answerId, commentId, null);
    }

    /** 我关注的人发布了问题 */
    public void createQuestionPublishNotifications(String questionId) {
        QuestionDO q = questionRepository.findByQuestionId(questionId);
        if (q == null) return;
        Long ownerId = q.getAuthorId();
        if (ownerId == null) return;
        List<UserFollowDO> followers = userFollowMapper.selectFollowers(ownerId);
        if (followers == null || followers.isEmpty()) return;
        for (UserFollowDO follower : followers) {
            Long followerId = follower.getFollowerId();
            if (Objects.equals(followerId, ownerId)) continue;
            saveQuestionNotification(followerId, ownerId, NotificationType.FOLLOWEE_PUBLISH_QUESTION, questionId, null, null, null);
        }
    }

    // ================== 内部工具 ==================

    private NotificationDO buildBase(Long receiverId, Long actorId, NotificationType type,
                                     String targetType, String targetId) {
        String actorName = userMapper.selectNameById(actorId);
        return NotificationDO.builder()
                .receiverId(receiverId)
                .actorId(actorId)
                .actorName(actorName)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 问答场景下的通知创建，带 answerId / commentId / replyId，便于前端精确定位
     */
    private void saveQuestionNotification(Long receiverId, Long actorId, NotificationType type,
                                          String questionId,
                                          Long answerId, Long commentId, Long replyId) {
        try {
            NotificationDO entity = buildBase(receiverId, actorId, type, "QUESTION", questionId);
            entity.setAnswerId(answerId);
            entity.setCommentId(commentId);
            entity.setReplyId(replyId);
            entity.setMessage(buildMessage(entity));
            notificationRepository.save(entity);
        } catch (Exception e) {
            log.warn("保存问答通知失败 type={} questionId={} answerId={} commentId={} replyId={}",
                    type, questionId, answerId, commentId, replyId, e);
        }
    }

    private void saveSimpleNotification(Long receiverId, Long actorId, NotificationType type,
                                        String targetType, String targetId) {
        try {
            NotificationDO entity = buildBase(receiverId, actorId, type, targetType, targetId);
            entity.setMessage(buildMessage(entity));
            notificationRepository.save(entity);
        } catch (Exception e) {
            log.warn("保存通知失败 type={} targetType={} targetId={}", type, targetType, targetId, e);
        }
    }

    private String buildMessage(NotificationDO n) {
        String actor = n.getActorName() != null ? n.getActorName() : "有人";
        return switch (n.getType()) {
            case ANSWER_QUESTION -> actor + " 回答了你的问题";
            case LIKE_NOTE -> actor + " 赞同了你的笔记";
            case FAVORITE_NOTE -> actor + " 收藏了你的笔记";
            case NOTE_COMMENT -> actor + " 评论了你的笔记";
            case NOTE_COMMENT_REPLY -> actor + " 回复了你的评论";
            case LIKE_QUESTION -> actor + " 赞同了你的问题";
            case LIKE_ANSWER -> actor + " 赞同了你的回答";
            case LIKE_QA_COMMENT -> actor + " 赞同了你在问答下的评论";
            case LIKE_QA_REPLY -> actor + " 赞同了你在问答下的回复";
            case FAVORITE_QUESTION -> actor + " 收藏了你的问题";
            case QA_COMMENT -> actor + " 评论了你的回答";
            case QA_REPLY -> actor + " 回复了你在问答下的评论";
            case FOLLOWEE_PUBLISH_NOTE -> actor + " 发布了一篇新的笔记";
            case FOLLOWEE_PUBLISH_QUESTION -> actor + " 发布了一个新的问题";
            case NOTE_MODERATION_APPROVED -> n.getMessage() != null ? n.getMessage() : actor + " 通过了你的笔记审查";
            case NOTE_MODERATION_REJECTED -> n.getMessage() != null ? n.getMessage() : actor + " 未通过你的笔记审查";
        };
    }

    private NotificationVO toVO(NotificationDO entity) {
        return NotificationVO.builder()
                .id(entity.getId())
                .receiverId(entity.getReceiverId())
                .actorId(entity.getActorId())
                .actorName(entity.getActorName())
                .type(entity.getType())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .answerId(entity.getAnswerId())
                .commentId(entity.getCommentId())
                .replyId(entity.getReplyId())
                .read(entity.getRead())
                .createdAt(entity.getCreatedAt())
                .message(entity.getMessage())
                .build();
    }
}

