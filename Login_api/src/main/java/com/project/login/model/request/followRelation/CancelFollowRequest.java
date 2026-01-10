package com.project.login.model.request.followRelation;

import lombok.Data;

@Data
public class CancelFollowRequest {
    private Long user_id;
    private Long follow_user_id;
}
