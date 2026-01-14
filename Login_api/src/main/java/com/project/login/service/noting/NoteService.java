package com.project.login.service.noting;

import com.project.login.convert.NoteConvert;
import com.project.login.exception.DuplicateNoteTitleException;
import com.project.login.mapper.*;
import com.project.login.model.dataobject.*;
import com.project.login.model.dto.note.*;
import com.project.login.model.event.EsNoteEvent;
import com.project.login.model.event.NoteActionType;
import com.project.login.model.vo.NoteShowVO;
import com.project.login.model.vo.NoteVO;
import com.project.login.service.minio.MinioService;
import com.project.login.service.notification.NotificationService;
import com.project.login.repository.NoteRepository;
import com.project.login.model.entity.NoteEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j // 假设使用 Slf4j 进行日志记录
public class NoteService {

    private final NoteMapper noteMapper;
    private final NotebookMapper notebookMapper;
    private final NoteSpaceMapper notespaceMapper;
    private final NoteStatsMapper noteStatsMapper;
    private final UserMapper userMapper;
    private final MinioService minioservice;
    private final NoteEventPublisher eventPublisher;
    private final ContentSummaryService contentSummaryService;
    private final NotificationService notificationService;
    private final NoteModerationMapper noteModerationMapper;
    private final NoteRepository noteRepository;

    @Qualifier("noteConvert")
    private final NoteConvert convert;

    private String getAuthorName(Long id) {
        Long bookId = noteMapper.selectNotebookIdByNoteId(id);
        Long spaceId = notebookMapper.selectSpaceIdByNotebookId(bookId);
        Long userId = notespaceMapper.selectUserIdBySpaceId(spaceId);
        return userMapper.selectNameById(userId);
    }


    @Transactional
    public NoteVO createNote(NoteCreateDTO dto) {

        if (notebookMapper.selectById(dto.getMeta().getNotebookId()) == null) {
            throw new RuntimeException("笔记本不存在");
        }

        // 保证：同一用户的同一笔记空间下，同一笔记本中笔记标题不重复
        // 由于 notebook 归属于某个 space，而 space 又归属于某个 user，
        // 因此在同一 notebook 下保证 title 唯一即可满足业务约束
        NoteDO duplicated = noteMapper.selectByNotebookIdAndTitle(
                dto.getMeta().getNotebookId(),
                dto.getMeta().getTitle()
        );
        if (duplicated != null) {
            throw new DuplicateNoteTitleException("同一笔记本下已存在同名笔记");
        }

        // 上传文件并获取文件名和URL
        MultipartFile file = dto.getFile();
        String contentSummary = contentSummaryService.extractContentSummary(file);

        //minio 名字
        String name = minioservice.uploadFile(file);
        String url = minioservice.getFileUrl(name);

        NoteDO note = NoteDO.builder()
                .title(dto.getMeta().getTitle())
                .fileType(dto.getMeta().getFileType())
                .filename(name)
                .notebookId(dto.getMeta().getNotebookId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        noteMapper.insert(note);

        NoteStatsDO stats = NoteStatsDO.builder()
                .noteId(note.getId())
                .authorName(getAuthorName(note.getId()))
                .views(0L)
                .likes(0L)
                .favorites(0L)
                .comments(0L)
                .lastActivityAt(LocalDateTime.now())
                .version(0L)
                .updatedAt(LocalDateTime.now())
                .build();
        noteStatsMapper.insert(stats);

        return convert.toVO(note);
    }


    @Transactional
    public NoteVO updateNote(NoteUpdateDTO dto) {

        NoteDO existing = noteMapper.selectById(dto.getMeta().getId());
        if (existing == null) throw new RuntimeException("笔记不存在");

        // 检查笔记是否在审核中
        if (isNoteUnderModeration(existing.getId())) {
            throw new RuntimeException("笔记正在审核中，无法修改");
        }

        // 上传文件并获取文件名和URL
        MultipartFile file = dto.getFile();
        String contentSummary = contentSummaryService.extractContentSummary(file);
        String name = minioservice.uploadFile(file);

        existing.setTitle(dto.getMeta().getTitle());
        existing.setFileType(dto.getMeta().getFileType());
        existing.setFilename(name);
        existing.setUpdatedAt(LocalDateTime.now());

        noteMapper.update(existing);

        NoteStatsDO stats = noteStatsMapper.getById(existing.getId());
        if (stats != null) {
            stats.setUpdatedAt(LocalDateTime.now());
            noteStatsMapper.updateOptimistic(stats);
        }

        return convert.toVO(existing);
    }

    @Transactional
    public void deleteNote(NoteDeleteDTO dto) {

        NoteDO existing = noteMapper.selectById(dto.getNoteId());
        if (existing == null) throw new RuntimeException("笔记不存在");

        // 检查笔记是否在审核中
        if (isNoteUnderModeration(existing.getId())) {
            throw new RuntimeException("笔记正在审核中，无法删除");
        }

        minioservice.deleteFile(existing.getFilename());

        noteMapper.deleteById(dto.getNoteId());

        noteStatsMapper.deleteById(dto.getNoteId());

        // --- 发布异步更新 ES 事件 ---
        EsNoteEvent event = new EsNoteEvent();
        event.setNoteId(existing.getId());
        event.setAction(NoteActionType.DELETE);

        eventPublisher.sendEsNoteEvent(event);
    }


    @Transactional
    public NoteVO moveNote(NoteMoveDTO dto) {

        NoteDO note = noteMapper.selectById(dto.getNoteId());
        if (note == null) throw new RuntimeException("笔记不存在");

        // 检查笔记是否在审核中
        if (isNoteUnderModeration(note.getId())) {
            throw new RuntimeException("笔记正在审核中，无法移动");
        }

        if (notebookMapper.selectById(dto.getTargetNotebookId()) == null) {
            throw new RuntimeException("笔记本不存在");
        }

        note.setNotebookId(dto.getTargetNotebookId());
        note.setUpdatedAt(LocalDateTime.now());

        noteMapper.update(note);

        return convert.toVO(note);
    }


    @Transactional(readOnly = true)
    public List<NoteVO> getNotesByNotebook(NoteListByNotebookDTO dto) {
        if (notebookMapper.selectById(dto.getNotebookID()) == null) {
            throw new RuntimeException("笔记本不存在");
        }
        List<NoteDO> doList = noteMapper.selectVOListByNotebookId(dto.getNotebookID());
        return convert.toVOList(doList);
    }

    // --- 文件/图片操作 ---
    @Transactional
    public NoteVO uploadFile(NoteUploadFileDTO dto) {

        if (notebookMapper.selectById(dto.getMeta().getNotebookId()) == null) {
            throw new RuntimeException("笔记本不存在");
        }

        // 与 createNote 一致：防止同一笔记本下创建重名笔记
        NoteDO duplicated = noteMapper.selectByNotebookIdAndTitle(
                dto.getMeta().getNotebookId(),
                dto.getMeta().getTitle()
        );
        if (duplicated != null) {
            throw new DuplicateNoteTitleException("同一笔记本下已存在同名笔记");
        }

        // 上传文件并获取文件名和URL
        MultipartFile file = dto.getFile();
        String contentSummary = contentSummaryService.extractContentSummary(file);
        String name = minioservice.uploadFile(file);
        String url = minioservice.getFileUrl(name);

        NoteDO note = NoteDO.builder()
                .title(dto.getMeta().getTitle())
                .fileType(dto.getMeta().getFileType())
                .filename(name)
                .notebookId(dto.getMeta().getNotebookId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        noteMapper.insert(note);

        // --- 初始化 note_stats ---
        NoteStatsDO stats = NoteStatsDO.builder()
                .noteId(note.getId())
                .authorName(getAuthorName(note.getId()))
                .views(0L)
                .likes(0L)
                .favorites(0L)
                .comments(0L)
                .lastActivityAt(LocalDateTime.now())
                .version(0L)
                .updatedAt(LocalDateTime.now())
                .build();
        noteStatsMapper.insert(stats);

        return convert.toVO(note);
    }


    @Transactional(readOnly = true)
    public String getFileUrl(NoteFileUrlDTO dto) {
        String url = minioservice.getFileUrl(dto.getFilename());
        return url;
    }

    @Transactional(readOnly = true)
    public NoteShowVO getNoteByNoteId(Long noteId) {
        NoteDO existing = noteMapper.selectById(noteId);
        if (existing == null) throw new RuntimeException("笔记不存在");
        String url = minioservice.getFileUrl(existing.getFilename());
        NoteShowVO vo = new NoteShowVO();
        vo.setId(existing.getId());
        vo.setTitle(existing.getTitle());
        vo.setUrl(url);
        vo.setFileType(existing.getFileType());
        vo.setNotebookId(existing.getNotebookId());
        // 获取 spaceId
        if (existing.getNotebookId() != null) {
            Long spaceId = notebookMapper.selectSpaceIdByNotebookId(existing.getNotebookId());
            vo.setSpaceId(spaceId);
        }
        vo.setCreatedAt(existing.getCreatedAt());
        vo.setUpdatedAt(existing.getUpdatedAt());
        return vo;
    }


    @Transactional
    public String uploadImage(ImageUrlDTO dto) {
        // 上传文件并获取文件名和URL
        MultipartFile file = dto.getFile();
        String name = minioservice.uploadFile(file);
        String url = minioservice.getFileUrl(name);
        return url;
    }

    @Transactional
    public NoteVO renameNote(NoteRenameDTO dto) {
        // 1. 查找
        NoteDO note = noteMapper.selectById(dto.getId());
        if (note == null) throw new RuntimeException("笔记不存在");
        
        // 检查笔记是否在审核中
        if (isNoteUnderModeration(note.getId())) {
            throw new RuntimeException("笔记正在审核中，无法重命名");
        }
        
        // 2. 检查重命名后的名称是否在同一笔记本下已存在（排除当前笔记本身）
        // 如果新名称和旧名称相同，则允许（不需要检查）
        if (!note.getTitle().equals(dto.getNewName())) {
            NoteDO duplicated = noteMapper.selectByNotebookIdAndTitle(
                    note.getNotebookId(),
                    dto.getNewName()
            );
            // 如果找到了同名笔记，且不是当前笔记本身，则不允许重命名
            if (duplicated != null && !duplicated.getId().equals(note.getId())) {
                throw new DuplicateNoteTitleException("同一笔记本下已存在同名笔记");
            }
        }
        
        // 3. 更新名称
        note.setTitle(dto.getNewName());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.update(note);

        NoteStatsDO stats = noteStatsMapper.getById(note.getId());
        if (stats != null) {
            stats.setAuthorName(getAuthorName(note.getId()));
            noteStatsMapper.updateOptimistic(stats);
        }

        return convert.toVO(note);
    }

    @Transactional
    public NoteVO publishNote(NotePublishDTO dto) {
        // 查找笔记
        NoteDO existing = noteMapper.selectById(dto.getMeta().getId());
        if (existing == null) throw new RuntimeException("笔记不存在");

        // 上传文件并获取文件名和URL
        MultipartFile file = dto.getFile();
        String contentSummary = contentSummaryService.extractContentSummary(file);
        String name = minioservice.uploadFile(file);

        existing.setTitle(dto.getMeta().getTitle());
        existing.setFileType(dto.getMeta().getFileType());
        existing.setFilename(name);
        existing.setUpdatedAt(LocalDateTime.now());

        noteMapper.update(existing);

        NoteStatsDO stats = noteStatsMapper.getById(existing.getId());
        if (stats != null) {
            stats.setUpdatedAt(LocalDateTime.now());
            noteStatsMapper.updateOptimistic(stats);
        }

        // --- 发布异步更新 ES 事件 ---
        EsNoteEvent event = new EsNoteEvent();
        event.setNoteId(existing.getId());
        event.setAction(NoteActionType.UPDATE);

        event.setTitle(existing.getTitle());
        event.setContentSummary(contentSummary);

        eventPublisher.sendEsNoteEvent(event);

        // 向关注该用户的粉丝发送“我关注的人发布了笔记”通知
        notificationService.createNotePublishNotifications(existing.getId());

        return convert.toVO(existing);
    }

    @Transactional(readOnly = true)
    public Long getNoteCount() {
        // 与 getAllNotes() 保持一致的逻辑：只统计已发布且未审核中的笔记
        // 1. 先查询有note_stats记录且不在审核中的笔记
        List<NoteDO> doList = noteMapper.selectPublished();
        
        if (doList.isEmpty()) {
            return 0L;
        }
        
        // 2. 批量查询 Elasticsearch，获取已发布的笔记ID列表
        // 因为只有 publishNote 才会发送 ES 事件，所以 ES 中有记录 = 已发布
        List<Long> publishedNoteIds;
        try {
            Iterable<NoteEntity> esEntities = noteRepository.findAllById(
                    doList.stream().map(NoteDO::getId).collect(Collectors.toList())
            );
            publishedNoteIds = new ArrayList<>();
            for (NoteEntity entity : esEntities) {
                if (entity != null && entity.getId() != null) {
                    publishedNoteIds.add(entity.getId());
                }
            }
        } catch (Exception e) {
            log.error("批量查询ES中的笔记失败", e);
            // 如果ES查询失败，返回0（安全起见）
            return 0L;
        }
        
        // 3. 统计在 Elasticsearch 中有记录的笔记数量（已发布）
        return (long) publishedNoteIds.size();
    }

    @Transactional(readOnly = true)
    public List<NoteShowVO> getAllNotes() {
        // 只返回已发布的笔记（有note_stats记录），排除审核中的笔记
        List<NoteDO> doList = noteMapper.selectPublished();
        
        if (doList.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 批量查询 Elasticsearch，获取已发布的笔记ID列表
        // 因为只有 publishNote 才会发送 ES 事件，所以 ES 中有记录 = 已发布
        List<Long> publishedNoteIds;
        try {
            Iterable<NoteEntity> esEntities = noteRepository.findAllById(
                    doList.stream().map(NoteDO::getId).collect(Collectors.toList())
            );
            publishedNoteIds = new ArrayList<>();
            for (NoteEntity entity : esEntities) {
                if (entity != null && entity.getId() != null) {
                    publishedNoteIds.add(entity.getId());
                }
            }
        } catch (Exception e) {
            log.error("批量查询ES中的笔记失败", e);
            // 如果ES查询失败，返回空列表（安全起见）
            return new ArrayList<>();
        }
        
        // 过滤掉未发布的笔记：只保留在 Elasticsearch 中有记录的笔记
        List<NoteDO> publishedList = doList.stream()
                .filter(noteDO -> publishedNoteIds.contains(noteDO.getId()))
                .collect(Collectors.toList());
        
        List<NoteShowVO> voList = new ArrayList<>();
        for (NoteDO noteDO : publishedList) {
            NoteShowVO vo = new NoteShowVO();
            vo.setId(noteDO.getId());
            vo.setTitle(noteDO.getTitle());
            vo.setFileType(noteDO.getFileType());
            vo.setNotebookId(noteDO.getNotebookId());
            vo.setCreatedAt(noteDO.getCreatedAt());
            vo.setUpdatedAt(noteDO.getUpdatedAt());
            // 获取文件访问URL
            if (noteDO.getFilename() != null && !noteDO.getFilename().isEmpty()) {
                String url = minioservice.getFileUrl(noteDO.getFilename());
                vo.setUrl(url);
            }
            // 获取作者信息（用户名和邮箱）
            try {
                Long bookId = noteMapper.selectNotebookIdByNoteId(noteDO.getId());
                if (bookId != null) {
                    Long spaceId = notebookMapper.selectSpaceIdByNotebookId(bookId);
                    if (spaceId != null) {
                        Long userId = notespaceMapper.selectUserIdBySpaceId(spaceId);
                        if (userId != null) {
                            UserDO user = userMapper.selectById(userId);
                            if (user != null) {
                                vo.setAuthorName(user.getUsername());
                                vo.setAuthorEmail(user.getEmail());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 如果获取作者信息失败，记录日志但不影响主流程
                log.warn("获取笔记{}的作者信息失败", noteDO.getId(), e);
            }
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 检查笔记是否在审核中
     * @param noteId 笔记ID
     * @return true=在审核中，false=不在审核中
     */
    private boolean isNoteUnderModeration(Long noteId) {
        try {
            // 直接使用Mapper查询，避免循环依赖
            List<com.project.login.model.dataobject.NoteModerationDO> moderationList = noteModerationMapper.selectByNoteId(noteId);
            if (moderationList == null || moderationList.isEmpty()) {
                return false;
            }
            // 如果存在未处理的审核记录（status=FLAGGED且isHandled=false），说明笔记在审核中
            return moderationList.stream()
                    .anyMatch(m -> "FLAGGED".equals(m.getStatus()) && Boolean.FALSE.equals(m.getIsHandled()));
        } catch (Exception e) {
            log.warn("检查笔记审核状态失败 noteId={}", noteId, e);
            // 检查失败时，为了安全起见，返回true（阻止操作）
            return true;
        }
    }

}