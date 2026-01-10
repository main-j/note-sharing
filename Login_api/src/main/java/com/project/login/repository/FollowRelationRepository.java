package com.project.login.repository;

import com.project.login.model.dataobject.FollowRelationDO;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FollowRelationRepository
        extends MongoRepository<FollowRelationDO, Long> {

    FollowRelationDO findByUserId(Long userId);


}
