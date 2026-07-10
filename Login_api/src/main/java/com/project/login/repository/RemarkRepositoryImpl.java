package com.project.login.repository;

import com.project.login.model.dataobject.RemarkDO;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RemarkRepositoryImpl implements RemarkRepositoryCustom {

    private final MongoTemplate mongoTemplate;
    private final MongoConverter mongoConverter;

    @Override
    public Optional<RemarkDO> findByRemarkId(String remarkId) {
        if (remarkId == null || remarkId.isBlank()) {
            return Optional.empty();
        }
        Document doc = mongoTemplate.getCollection("remark")
                .find(new Document("_id", remarkId))
                .first();
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(mongoConverter.read(RemarkDO.class, doc));
    }
}
