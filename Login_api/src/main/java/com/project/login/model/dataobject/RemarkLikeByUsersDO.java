package com.project.login.model.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection="remark_likes")
public class RemarkLikeByUsersDO {
    @Id
    private String remarkId;

    @Field("user_list")
    private Set<Long> userList;

    @Version
    private Long version;
}