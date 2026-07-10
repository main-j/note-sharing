package com.project.login.mapper;

import com.project.login.model.dataobject.UserLikeNoteDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserLikeNoteMapper {

    @Insert("""
        INSERT INTO user_like_note (user_id, note_id, like_time)
        VALUES (#{userId}, #{noteId}, NOW())
        ON DUPLICATE KEY UPDATE like_time = NOW()
    """)
    int insert(UserLikeNoteDO like);

    @Delete("""
        DELETE FROM user_like_note
        WHERE user_id = #{userId} AND note_id = #{noteId}
    """)
    int delete(@Param("userId") Long userId, @Param("noteId") Long noteId);

    @Select("""
        SELECT COUNT(1)
        FROM user_like_note
        WHERE user_id = #{userId} AND note_id = #{noteId}
    """)
    int exists(@Param("userId") Long userId, @Param("noteId") Long noteId);

    @Select("""
        SELECT note_id
        FROM user_like_note
        WHERE user_id = #{userId}
        ORDER BY like_time DESC
    """)
    List<Long> selectNoteIdsByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*)
        FROM user_like_note
        WHERE note_id = #{noteId}
    """)
    int countByNoteId(@Param("noteId") Long noteId);
}
