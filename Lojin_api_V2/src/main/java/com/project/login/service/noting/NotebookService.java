package com.project.login.service.noting;

import com.project.login.model.dto.request.notebook.*;
import com.project.login.model.entity.*;
import com.project.login.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotebookService {

    private final NotebookRepository notebookRepository;
    private final NoteRepository noteRepository;
    private final NoteSpaceRepository noteSpaceRepository;
    private final TagRepository tagRepository;

    // --- 创建笔记本 ---
    @Transactional
    public NotebookEntity createNotebook(NotebookCreateRequest req) {
        var space = noteSpaceRepository.findById(req.getSpaceId())
                .orElseThrow(() -> new RuntimeException("笔记空间不存在"));

        // 查找标签，如果不存在就创建
        TagEntity tag = tagRepository.findByName(req.getTag())
                .orElseGet(() -> {
                    TagEntity newTag = new TagEntity();
                    newTag.setName(req.getTag());
                    return tagRepository.save(newTag);
                });

        NotebookEntity notebook = new NotebookEntity();
        notebook.setName(req.getName());
        notebook.setSpace(space);
        notebook.setTag(tag);
        notebook.setCreatedAt(LocalDateTime.now());
        notebook.setUpdatedAt(LocalDateTime.now());

        return notebookRepository.save(notebook);
    }

    // --- 笔记本重命名 / 更新标签 ---
    @Transactional
    public NotebookEntity updateNotebook(@Valid NotebookUpdateRequest req) {
        NotebookEntity notebook = notebookRepository.findById(req.getId())
                .orElseThrow(() -> new RuntimeException("笔记本不存在"));

        notebook.setName(req.getName());
        notebook.setUpdatedAt(LocalDateTime.now());

        // 更新标签，如果不存在就创建
        TagEntity tag = tagRepository.findByName(req.getTag())
                .orElseGet(() -> {
                    TagEntity newTag = new TagEntity();
                    newTag.setName(req.getTag());
                    return tagRepository.save(newTag);
                });
        notebook.setTag(tag);

        return notebookRepository.save(notebook);
    }

    // --- 删除笔记本（级联删除笔记） ---
    @Transactional
    public void deleteNotebook(@Valid NotebookDeleteRequest req) {
        NotebookEntity notebook = notebookRepository.findById(req.getId())
                .orElseThrow(() -> new RuntimeException("笔记本不存在"));

        // 删除该笔记本下的所有笔记
        noteRepository.deleteByNotebookId(notebook.getId());

        notebookRepository.delete(notebook);
    }

    // --- 移动笔记本到另一个笔记空间 ---
    @Transactional
    public NotebookEntity moveNotebook(@Valid NotebookMoveRequest req) {
        NotebookEntity notebook = notebookRepository.findById(req.getNotebookId())
                .orElseThrow(() -> new RuntimeException("笔记本不存在"));

        NoteSpaceEntity targetSpace = noteSpaceRepository.findById(req.getTargetNoteSpaceId())
                .orElseThrow(() -> new RuntimeException("目标笔记空间不存在"));

        notebook.setSpace(targetSpace);
        notebook.setUpdatedAt(LocalDateTime.now());

        return notebookRepository.save(notebook);
    }

    // --- 根据笔记空间获取所有笔记本 ---
    @Transactional(readOnly = true)
    public List<NotebookEntity> getNotebooksBySpace(@Valid NotebookListBySpaceRequest req) {
        return notebookRepository.findBySpaceId(req.getSpaceId());
    }
}
