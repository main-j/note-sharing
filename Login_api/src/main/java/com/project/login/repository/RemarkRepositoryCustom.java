package com.project.login.repository;

import com.project.login.model.dataobject.RemarkDO;

import java.util.Optional;

public interface RemarkRepositoryCustom {
    Optional<RemarkDO> findByRemarkId(String remarkId);
}
