CREATE TABLE IF NOT EXISTS app_usage_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(191) NULL,
    ip_address VARCHAR(45) NULL,
    device_model VARCHAR(255) NULL,
    os_version VARCHAR(64) NULL,
    app_version VARCHAR(64) NULL,
    launch_time BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
