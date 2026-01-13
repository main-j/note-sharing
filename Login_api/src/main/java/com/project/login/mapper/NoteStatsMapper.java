package com.project.login.mapper;

import com.project.login.model.dataobject.NoteStatsDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteStatsMapper {

    @Select("SELECT * FROM note_stats WHERE note_id = #{noteId}")
    NoteStatsDO getById(@Param("noteId") Long noteId);

    @Insert("INSERT INTO note_stats(note_id, author_name, views, likes, favorites, comments, last_activity_at, version) " +
            "VALUES(#{noteId}, #{authorName}, #{views}, #{likes}, #{favorites}, #{comments}, #{lastActivityAt}, #{version})")
    void insert(NoteStatsDO noteStats);

    /** -----------------------------
     * 乐观锁更新
     * ----------------------------- */
    @Update("UPDATE note_stats " +
            "SET author_name=#{authorName}, " +
            "views=#{views}, likes=#{likes}, favorites=#{favorites}, comments=#{comments}, " +
            "last_activity_at=#{lastActivityAt}, version=version+1 " +
            "WHERE note_id=#{noteId} AND version=#{version}")
    int updateOptimistic(NoteStatsDO noteStats);

    @Select("SELECT * FROM note_stats ORDER BY last_activity_at DESC LIMIT #{limit}")
    List<NoteStatsDO> getRecentUpdated(@Param("limit") int limit);

    @Select({
            "<script>",
            "SELECT * FROM note_stats WHERE note_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<NoteStatsDO> getByIds(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM note_stats WHERE note_id = #{noteId}")
    int deleteById(@Param("noteId") Long noteId);

    /** 增量更新（乐观锁），使用GREATEST防止负数 */
    @Update("UPDATE note_stats " +
            "SET views = GREATEST(0, views + #{views}), " +
            "likes = GREATEST(0, likes + #{likes}), " +
            "favorites = GREATEST(0, favorites + #{favorites}), " +
            "comments = GREATEST(0, comments + #{comments}), " +
            "last_activity_at = #{lastActivityAt}, " +
            "version = version + 1 " +
            "WHERE note_id = #{noteId} AND version = #{version}")
    int incrementByDeltas(NoteStatsDO noteStats);

    /** 覆盖 totals（乐观锁），使用GREATEST防止负数 */
    @Update("UPDATE note_stats " +
            "SET author_name=#{authorName}, " +
            "views=GREATEST(0, #{views}), " +
            "likes=GREATEST(0, #{likes}), " +
            "favorites=GREATEST(0, #{favorites}), " +
            "comments=GREATEST(0, #{comments}), " +
            "last_activity_at=#{lastActivityAt}, " +
            "version = version + 1 " +
            "WHERE note_id = #{noteId} AND version = #{version}")
    int updateTotalsIfVersion(NoteStatsDO noteStats);
}
