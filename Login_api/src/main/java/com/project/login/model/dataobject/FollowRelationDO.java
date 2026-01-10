package com.project.login.model.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "user_follow")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowRelationDO {
    @Id
    private Long userId;

    private List<FollowUser> following;

    private List<FollowUser> followers;

    private LocalDateTime updateTime;

}

