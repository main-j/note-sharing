package com.project.login.model.vo.followRelation;

import com.project.login.model.dataobject.FollowRelationDO;
import com.project.login.model.dataobject.FollowUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetFollowersVO {
    private Long user_id;
    private List<FollowUser> followers;
}
