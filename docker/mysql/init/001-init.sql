SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS schema_version (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    version_no      VARCHAR(32) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    installed_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_schema_version_no (version_no)
) ENGINE=InnoDB COMMENT='数据库初始化版本';

INSERT INTO schema_version (version_no, description)
VALUES ('0.1.0', 'StockPilot first backend skeleton')
ON DUPLICATE KEY UPDATE description = VALUES(description);
