package com.project.login.convert;

import com.project.login.model.dataobject.FollowRelationDO;
import com.project.login.model.dto.followRelation.*;
import com.project.login.model.request.followRelation.AddFollowRequest;
import com.project.login.model.request.followRelation.CancelFollowRequest;
import com.project.login.model.request.followRelation.GetFollowStatusRequest;
import com.project.login.model.request.followRelation.GetFollowersRequest;
import com.project.login.model.vo.followRelation.GetFollowersVO;
import com.project.login.model.vo.followRelation.GetFollowingsVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface FollowRelationConvert {
    FollowRelationConvert INSTANCE = Mappers.getMapper(FollowRelationConvert.class);
    AddFollowDTO toAddFollowDTO(AddFollowRequest req);
    CancelFollowDTO toCancelFollowDTO(CancelFollowRequest req);
    GetFollowersDTO toGetFollowersDTO(GetFollowersRequest req);
    GetFollowingsDTO toGetFollowingsDTO(GetFollowersRequest req);
    GetFollowStatusDTO toGetFollowStatusDTO(GetFollowStatusRequest req);
    @Mappings({
            @Mapping(source = "userId", target = "user_id"),
            @Mapping(source = "followers", target = "followers")
    })
    GetFollowersVO toFollowersVO(FollowRelationDO followRelationDO);


    @Mappings({
            @Mapping(source = "userId", target = "user_id"),
            @Mapping(source = "following", target = "followings")
    })
    GetFollowingsVO toFollowingsVO(FollowRelationDO followRelationDO);

}
