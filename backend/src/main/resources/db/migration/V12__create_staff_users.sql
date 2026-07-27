CREATE TABLE staff_users (
  id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username                VARCHAR(100) NOT NULL,
  password_hash           VARCHAR(60)  NOT NULL,
  role                    ENUM('ROLE_STAFF','ROLE_PROVIDER','ROLE_ADMIN','ROLE_SYSADMIN') NOT NULL,
  provider_id             BIGINT UNSIGNED NULL,
  is_active               BOOLEAN NOT NULL DEFAULT TRUE,
  failed_login_attempts   SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  locked_until            DATETIME(3) NULL,
  last_login_at           DATETIME(3) NULL,
  created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  UNIQUE KEY uq_staff_username (username),
  INDEX idx_staff_role_active (role, is_active),
  INDEX idx_staff_provider (provider_id),
  CONSTRAINT chk_provider_role_pairing CHECK (
    (role = 'ROLE_PROVIDER' AND provider_id IS NOT NULL) OR
    (role <> 'ROLE_PROVIDER')
  )
) ENGINE=InnoDB;
