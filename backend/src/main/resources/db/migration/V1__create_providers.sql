CREATE TABLE providers (
  id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  first_name     VARCHAR(100) NOT NULL,
  last_name      VARCHAR(100) NOT NULL,
  specialty      VARCHAR(150) NOT NULL,
  email          VARCHAR(254) NOT NULL UNIQUE,
  timezone       VARCHAR(64)  NOT NULL DEFAULT 'America/New_York',
  is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at     DATETIME(3)  NULL,
  INDEX idx_providers_active (is_active, deleted_at)
) ENGINE=InnoDB;
