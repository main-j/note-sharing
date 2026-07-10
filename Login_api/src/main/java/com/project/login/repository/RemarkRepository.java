package com.project.login.repository;

import com.project.login.model.dataobject.RemarkDO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RemarkRepository extends MongoRepository<RemarkDO, String>, RemarkRepositoryCustom {
    @Query("{'note_id': ?0, 'is_reply': false}")
    List<RemarkDO> findRemarksByNoteIdAndIsReplyFalse(Long noteId);

    @Query("{'parent_id': ?0, 'is_reply': true}")
    List<RemarkDO> findRemarksByParentIdAndIsReplyTrue(String parentId);

    @Query("{'user_id': ?0, 'is_reply': false}")
    List<RemarkDO> findByUserId(Long userId);

    void deleteByParentId(String parentId);
}
