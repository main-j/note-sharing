package com.project.login.model.dto.followRelation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelFollowDTO {
    private Long user_id;
    private Long follow_user_id;
}
