CREATE TABLE IF NOT EXISTS device_registry (
    device_id VARCHAR(191) PRIMARY KEY,
    first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_user_id VARCHAR(255) NULL,
    last_user_email VARCHAR(255) NULL,
    last_user_name VARCHAR(255) NULL,
    last_ip VARCHAR(45) NULL,
    device_model VARCHAR(255) NULL,
    os_version VARCHAR(64) NULL,
    app_version VARCHAR(64) NULL,
    offline_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSON NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_registry_status (offline_allowed, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
