CREATE TABLE IF NOT EXISTS user_like_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    note_id BIGINT NOT NULL COMMENT '笔记ID',
    like_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_note (user_id, note_id),
    INDEX idx_user_id (user_id),
    INDEX idx_note_id (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户点赞笔记关系表';
