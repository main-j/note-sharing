package com.project.login.model.dataobject;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection="remark_count")
public class RemarkCountDO {
    @Id
    private String remarkId;

    @Field("remark_like_count")
    private Long remarkLikeCount;

    @Version
    private Long version;

}