CREATE TABLE IF NOT EXISTS client_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    log_level VARCHAR(20) NOT NULL,
    request_url VARCHAR(255) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    response_code INT,
    is_successful BOOLEAN NOT NULL,
    duration_ms BIGINT NOT NULL,
    error_message TEXT,
    device_info TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;