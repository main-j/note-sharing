package com.project.login.model.request.followRelation;

import lombok.Data;

@Data
public class AddFollowRequest {
    private Long user_id;
    private Long follow_user_id;
}
