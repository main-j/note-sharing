package com.project.login.convert;

import com.project.login.model.dto.search.NoteSearchDTO;
import com.project.login.model.request.search.NoteSearchRequest;
import com.project.login.model.vo.NoteSearchVO;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface SearchConvert {

    // 请求对象转换
    NoteSearchDTO toSearchDTO(NoteSearchRequest request);

    // ES 返回 Object 转 VO（ES 返回的是 Map 风格）
    default NoteSearchVO toSearchVO(Object source) {
        if (source == null) return null;

        NoteSearchVO vo = new NoteSearchVO();

        // 假设 ES 返回的是 Map<String, Object>
        Map<String, Object> map = (Map<String, Object>) source;

        vo.setNoteId(resolveNoteId(map));
        vo.setTitle((String) map.get("title"));
        vo.setContentSummary((String) map.get("contentSummary")); // 对应 VO 字段

        // 统计字段，null 默认 0
        vo.setAuthorName(map.get("authorName") != null ? String.valueOf(map.get("authorName")) : "未知作者");
        vo.setViewCount(map.get("views") != null ? Integer.parseInt(map.get("views").toString()) : 0);
        vo.setLikeCount(map.get("likes") != null ? Integer.parseInt(map.get("likes").toString()) : 0);
        vo.setFavoriteCount(map.get("favorites") != null ? Integer.parseInt(map.get("favorites").toString()) : 0);
        vo.setCommentCount(map.get("comments") != null ? Integer.parseInt(map.get("comments").toString()) : 0);

        // 更新时间
        if (map.get("updatedAt") != null) {
            vo.setUpdatedAt(LocalDateTime.parse(map.get("updatedAt").toString()));
        }

        return vo;
    }

    default Long resolveNoteId(Map<String, Object> map) {
        Object id = map.get("id");
        if (id == null) {
            id = map.get("noteId");
        }
        if (id == null) {
            return null;
        }
        return Long.valueOf(id.toString());
    }

}
