package com.project.login.mapper;

import com.project.login.model.dataobject.NoteModerationDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteModerationMapper {

    @Insert("INSERT INTO note_moderation (note_id, status, risk_level, score, categories_json, findings_json, source, created_at, is_handled, admin_comment) " +
            "VALUES (#{noteId}, #{status}, #{riskLevel}, #{score}, #{categoriesJson}, #{findingsJson}, #{source}, #{createdAt}, #{isHandled}, #{adminComment})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(NoteModerationDO record);

    @Select("SELECT * FROM note_moderation WHERE status = 'FLAGGED' AND is_handled = FALSE ORDER BY created_at DESC")
    @Results(id = "BaseResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "noteId", column = "note_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "riskLevel", column = "risk_level"),
            @Result(property = "score", column = "score"),
            @Result(property = "categoriesJson", column = "categories_json"),
            @Result(property = "findingsJson", column = "findings_json"),
            @Result(property = "source", column = "source"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "isHandled", column = "is_handled"),
            @Result(property = "adminComment", column = "admin_comment")
    })
    List<NoteModerationDO> selectPendingFlagged();

    @Update("UPDATE note_moderation SET is_handled = #{isHandled}, admin_comment = #{adminComment} WHERE id = #{id}")
    void updateHandled(NoteModerationDO record);

    @Select("SELECT * FROM note_moderation WHERE id = #{id}")
    @ResultMap("BaseResultMap")
    NoteModerationDO selectById(Long id);
}

