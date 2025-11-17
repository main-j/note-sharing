package com.project.login.repository;

import com.project.login.model.entity.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<NoteEntity, Long> {

    // 根据笔记本ID查询笔记
    List<NoteEntity> findByNotebookId(Long notebookId);

    // 根据笔记本ID删除笔记
    void deleteByNotebookId(Long notebookId);
}
