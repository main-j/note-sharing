package com.project.login.mapper;

import com.project.login.model.dataobject.NoteDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteMapper {

    // --- INSERT ---

    @Insert("INSERT INTO notes (title, filename, file_type, notebook_id, created_at, updated_at) " +
            "VALUES (#{title}, #{filename}, #{fileType}, #{notebookId}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(NoteDO note);

    // --- SELECT ---

    @Select("SELECT id, title, filename, file_type, notebook_id, created_at, updated_at " +
            "FROM notes WHERE id = #{id}")
    @Results(id = "NoteBaseResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "title", property = "title"),
            @Result(column = "filename", property = "filename"),
            @Result(column = "file_type", property = "fileType"),
            @Result(column = "notebook_id", property = "notebookId"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    NoteDO selectById(Long id);

    // 通过 noteId 查询 notebookId
    @Select("SELECT notebook_id FROM notes WHERE id = #{id} LIMIT 1")
    Long selectNotebookIdByNoteId(Long id);

    @Select("SELECT filename FROM notes WHERE id = #{id} LIMIT 1")
    String selectFileNameByNoteId(Long id);


    @Select("SELECT id, title, filename, file_type, notebook_id, created_at, updated_at " +
            "FROM notes WHERE notebook_id = #{notebookId} ORDER BY updated_at DESC")
    @ResultMap("NoteBaseResultMap")
    List<NoteDO> selectByNotebookId(Long notebookId);

    /**
     * 在同一笔记本下，根据标题查找笔记
     * 用于保证「同一用户的同一笔记空间下同一笔记本中」笔记标题唯一
     */
    @Select("""
            SELECT id, title, filename, file_type, notebook_id, created_at, updated_at
            FROM notes
            WHERE notebook_id = #{notebookId}
              AND title = #{title}
            LIMIT 1
            """)
    @ResultMap("NoteBaseResultMap")
    NoteDO selectByNotebookIdAndTitle(@Param("notebookId") Long notebookId,
                                      @Param("title") String title);

    // --- UPDATE ---

    @Update("""
            UPDATE notes
            SET title = #{title},
                filename = #{filename},
                file_type = #{fileType},
                notebook_id = #{notebookId},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    void update(NoteDO note);

    // --- DELETE ---

    @Delete("DELETE FROM notes WHERE id = #{id}")
    void deleteById(Long id);

    @Delete("DELETE FROM notes WHERE notebook_id = #{notebookId}")
    void deleteByNotebookId(Long notebookId);

    @Select("SELECT id, title, filename, file_type, notebook_id, created_at, updated_at " +
            "FROM notes WHERE notebook_id = #{notebookId} ORDER BY updated_at DESC")
    @ResultMap("NoteBaseResultMap")
    List<NoteDO> selectVOListByNotebookId(Long notebookId);

    @Select("SELECT COUNT(*) FROM notes")
    Long count();

    @Select("""
            SELECT COUNT(*) 
            FROM note_stats ns
            WHERE ns.note_id NOT IN (
                SELECT note_id 
                FROM note_moderation 
                WHERE status = 'FLAGGED' AND is_handled = FALSE
            )
            """)
    Long countPublished();

    @Select("SELECT id, title, filename, file_type, notebook_id, created_at, updated_at " +
            "FROM notes ORDER BY id ASC")
    @ResultMap("NoteBaseResultMap")
    List<NoteDO> selectAll();

    /**
     * 查询已发布的笔记列表（排除审核中的笔记）
     * 已发布的笔记：在note_stats表中有记录
     * 排除审核中的笔记：在note_moderation表中status='FLAGGED'且is_handled=FALSE的笔记
     */
    @Select("""
            SELECT n.id, n.title, n.filename, n.file_type, n.notebook_id, n.created_at, n.updated_at
            FROM notes n
            INNER JOIN note_stats ns ON n.id = ns.note_id
            WHERE n.id NOT IN (
                SELECT note_id 
                FROM note_moderation 
                WHERE status = 'FLAGGED' AND is_handled = FALSE
            )
            ORDER BY n.id ASC
            """)
    @ResultMap("NoteBaseResultMap")
    List<NoteDO> selectPublished();
}
