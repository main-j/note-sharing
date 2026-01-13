CREATE TABLE IF NOT EXISTS note_moderation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    risk_level VARCHAR(16),
    score INT,
    categories_json TEXT,
    findings_json TEXT,
    source VARCHAR(16) DEFAULT 'LLM',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_handled BOOLEAN DEFAULT FALSE,
    admin_comment VARCHAR(255),
    INDEX idx_note_moderation_note_id (note_id),
    INDEX idx_note_moderation_status_handled (status, is_handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
