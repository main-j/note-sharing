package com.project.login.service.noting;

import com.project.login.model.dto.request.notespace.NoteSpaceCreateRequest;
import com.project.login.model.dto.request.notespace.NoteSpaceUpdateRequest;
import com.project.login.model.dto.request.notespace.NoteSpaceDeleteRequest;
import com.project.login.model.entity.NoteSpaceEntity;
import com.project.login.model.entity.NotebookEntity;
import com.project.login.model.entity.TagEntity;
import com.project.login.model.entity.UserEntity;
import com.project.login.repository.NoteSpaceRepository;
import com.project.login.repository.NotebookRepository;
import com.project.login.repository.NoteRepository;
import com.project.login.repository.TagRepository;
import com.project.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteSpaceService {

    private final NoteSpaceRepository noteSpaceRepository;
    private final NotebookRepository notebookRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    // --- 创建笔记空间 ---
    @Transactional
    public NoteSpaceEntity createSpace(NoteSpaceCreateRequest req) {
        UserEntity user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        TagEntity tag = tagRepository.findByName(req.getTag())
                .orElseGet(() -> {
                    TagEntity newTag = new TagEntity();
                    newTag.setName(req.getTag());
                    return tagRepository.save(newTag);
                });

        NoteSpaceEntity space = new NoteSpaceEntity();
        space.setName(req.getName());
        space.setUser(user);
        space.setTag(tag);
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());

        noteSpaceRepository.save(space);
        return space;
    }

    // --- 修改笔记空间 ---
    @Transactional
    public NoteSpaceEntity renameSpace(NoteSpaceUpdateRequest req) {
        NoteSpaceEntity space = noteSpaceRepository.findById(req.getId())
                .orElseThrow(() -> new RuntimeException("笔记空间不存在"));

        space.setName(req.getName());
        space.setUpdatedAt(LocalDateTime.now());

        // 更新标签，如果不存在就创建
        TagEntity tag = tagRepository.findByName(req.getTag())
                .orElseGet(() -> {
                    TagEntity newTag = new TagEntity();
                    newTag.setName(req.getTag());
                    return tagRepository.save(newTag);
                });
        space.setTag(tag);
        noteSpaceRepository.save(space);
        return space;
    }

    // --- 删除笔记空间 ---
    @Transactional
    public void deleteSpace(NoteSpaceDeleteRequest req) {
        NoteSpaceEntity space = noteSpaceRepository.findById(req.getId())
                .orElseThrow(() -> new RuntimeException("笔记空间不存在"));

        // 删除该空间下的笔记和笔记本
        List<NotebookEntity> notebooks = notebookRepository.findBySpaceId(space.getId());
        for (NotebookEntity notebook : notebooks) {
            noteRepository.deleteByNotebookId(notebook.getId());
        }
        notebookRepository.deleteBySpaceId(space.getId());

        // 删除空间
        noteSpaceRepository.delete(space);
    }
}
