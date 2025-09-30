CREATE TABLE IF NOT EXISTS user_operations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    user_name VARCHAR(255),
    email VARCHAR(255),
    customer VARCHAR(255),
    frequency INT,
    intensity INT,
    operation_time INT,
    start_time DATETIME NOT NULL,
    stop_time DATETIME NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
