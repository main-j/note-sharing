package com.project.login.convert;

import com.project.login.model.dataobject.RemarkDO;
import com.project.login.model.dto.remark.RemarkDeleteDTO;
import com.project.login.model.dto.remark.RemarkInsertDTO;
import com.project.login.model.request.remark.RemarkDeleteRequest;
import com.project.login.model.vo.RemarkVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RemarkConvert {

    // Mapper 实例
    RemarkConvert INSTANCE = Mappers.getMapper(RemarkConvert.class);

    RemarkDeleteDTO toDeleteDTO(RemarkDeleteRequest req);

    @Mapping(target = "_id", ignore = true) // _id 是由 MongoDB 自动生成的
    @Mapping(target = "createdAt", ignore = true) // 默认设置创建时间为当前时间
    RemarkDO toDO(RemarkInsertDTO dto);

    @Mappings({
            @Mapping(source = "_id", target = "_id"),
            @Mapping(source = "noteId", target = "noteId"), // 映射 noteId
            @Mapping(source = "content", target = "content"), // 映射 content
            @Mapping(source = "createdAt", target = "createdAt"), // 映射 createdAt
            @Mapping(source = "parentId", target = "parentId"), // 映射 parentId
            @Mapping(source = "isReply", target = "isReply"), // 映射 isReceive
            @Mapping(source = "username",target = "username"),
            @Mapping(source = "userId",target="userId"),
            @Mapping(target = "likeCount",ignore=true),
            @Mapping(source = "replyToUsername",target ="replyToUsername"), // 使用 getReplyToUsername 方法填充 replyToUsername
            @Mapping(target = "replies", ignore = true), // 暂时不处理子评论
            @Mapping(target = "likedOrNot", ignore = true),// 当前用户是否已点赞，可能需要额外计算或通过其它查询填充
            @Mapping(target = "avatarUrl", ignore = true)
    })
    RemarkVO toVO(RemarkDO remarkDO);
    List<RemarkVO> toVOList(List<RemarkDO> remarkDOList);
}
