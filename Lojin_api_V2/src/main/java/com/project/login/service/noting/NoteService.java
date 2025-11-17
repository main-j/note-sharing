package com.project.login.service.noting;

import com.project.login.model.dto.request.note.*;
import com.project.login.model.entity.NoteEntity;
import com.project.login.model.entity.NotebookEntity;
import com.project.login.model.entity.TagEntity;
import com.project.login.repository.NoteRepository;
import com.project.login.repository.NotebookRepository;

import com.project.login.repository.TagRepository;
import com.project.login.service.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static java.lang.IO.println;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final TagRepository tagRepository;
    private final NoteRepository noteRepository;
    private final NotebookRepository notebookRepository;
    private final MinioService minioservice;

    @Value("${minio.bucket}")
    private String bucket;


    // 创建笔记
    @Transactional
    public Object createNote(NoteCreateRequest request) {
        try {
            // 根据meta中的notebookId获取原始笔记本
            NotebookEntity notebook = notebookRepository.findById(request.getMeta().getNotebookId())
                    .orElseThrow(() -> new RuntimeException("Notebook not found"));

            // --- 构建新笔记 ---
            NoteEntity note = new NoteEntity();
            note.setTitle(request.getMeta().getTitle());  // 设置标题
            note.setNotebook(notebook);                  // 关联笔记本


            // 设置时间戳
            LocalDateTime now = LocalDateTime.now();
            note.setCreatedAt(now);
            note.setUpdatedAt(now);

            // 上传文件并获取文件名和URL
            MultipartFile file = request.getFile();
            String filename = minioservice.uploadFile(file);
            String fileurl = minioservice.getFileUrl(filename);

            note.setFilename(filename);
            note.setFileType(request.getMeta().getFileType());

            // 保存笔记到数据库
            noteRepository.save(note);

            // 返回结果
            return Map.of(
                    "filename", filename,
                    "fileurl", fileurl,
                    "message", "Create new note success"
            );

        } catch (Exception e) {
            throw new RuntimeException("Create note failed", e);
        }
    }


    // Update note
    @Transactional
    public Object updateNote(NoteUpdateRequest request) {

        try {
            NoteEntity note = noteRepository.findById(request.getMeta().getNotebookId())
                    .orElseThrow(() -> new RuntimeException("Note not found"));

            note.setTitle(request.getMeta().getTitle());

            note.setUpdatedAt(LocalDateTime.now());

            MultipartFile file = request.getFile();
            // 删除minio中存储的原文件
            minioservice.deleteFile(note.getFilename());
            // 保存前端返回的更新后的文件，并获取filename、fileurl
            String filename = minioservice.uploadFile(file);
            String fileurl = minioservice.getFileUrl(filename);
            note.setFilename(filename);
            note.setFileType(request.getMeta().getFileType());

            return Map.of(
                    "filename", filename,
                    "fileurl", fileurl,
                    "message", "create new note success"

            );

        } catch (Exception e) {
            throw new RuntimeException("Update note failed", e);
        }
    }


    // Delete note
    @Transactional
    public void deleteNote(NoteDeleteRequest request) {

        NoteEntity note = noteRepository.findById(request.getNoteId())
                .orElseThrow(() -> new RuntimeException("Note not found"));

        // 获取note所属的notebook,并在其中删除该note
        NotebookEntity notebook = note.getNotebook();
        notebook.getNotes().remove(note);

        notebookRepository.save(notebook);
        // 删除minio中存储的文件
        minioservice.deleteFile(note.getFilename());
        // 数据库中删除该项
        noteRepository.delete(note);
    }


    // Move note to another notebook
    @Transactional
    public Object moveNote(NoteMoveRequest request) {

        try {
            NoteEntity note = noteRepository.findById(request.getNoteId())
                    .orElseThrow(() -> new RuntimeException("Note not found"));

            NotebookEntity notebook = notebookRepository.findById(request.getTargetNotebookId())
                    .orElseThrow(() -> new RuntimeException("Target notebook not found"));

            note.setNotebook(notebook);
            note.setUpdatedAt(LocalDateTime.now());

            return noteRepository.save(note);

        } catch (Exception e) {
            throw new RuntimeException("Move note failed", e);
        }
    }


    // Get notes by notebook ID
    public Object getNotesByNotebook(NoteListByNotebookRequest request) {
        return noteRepository.findByNotebookId(request.getNotebookID());
    }


    // Upload file to MinIO
    public Object uploadFile(NoteUploadFileRequest request) {

        try {
            // 根据meta中的id构建原notebook
            NotebookEntity notebook = notebookRepository.findById(request.getMeta().getNotebookId())
                    .orElseThrow(() -> new RuntimeException("Notebook not found"));

            // --- 构建新note ---
            NoteEntity note = new NoteEntity();
            // 保存meta中信息
            note.setTitle(request.getMeta().getTitle());
            note.setNotebook(notebook);

            note.setCreatedAt(LocalDateTime.now());
            note.setUpdatedAt(LocalDateTime.now());

            MultipartFile file = request.getFile();
            // 保存前端返回的更新后的文件，并获取filename、fileurl
            String filename = minioservice.uploadFile(file);
            String fileurl = minioservice.getFileUrl(filename);
            note.setFilename(filename);
            note.setFileType(request.getMeta().getFileType());

            // 将note保存到notebook中
            notebook.getNotes().add(note);
            notebookRepository.save(notebook);

            noteRepository.save(note);

            return Map.of(
                    "filename", filename,
                    "fileurl", fileurl,
                    "message", "create new note success"

            );

        } catch (Exception e) {
            throw new RuntimeException("Upload file failed", e);
        }
    }


    // Get MinIO file URL
    public Object getFileUrl(NoteFileUrlRequest request) {

        try {
            String url = minioservice.getFileUrl(request.getFilename());
            return Map.of("url", url);

        } catch (Exception e) {
            throw new RuntimeException("Get file URL failed", e);
        }
    }
}
