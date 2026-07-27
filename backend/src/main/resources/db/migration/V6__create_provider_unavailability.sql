CREATE TABLE provider_unavailability (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  provider_id     BIGINT UNSIGNED NOT NULL,
  start_datetime  DATETIME(3) NOT NULL,
  end_datetime    DATETIME(3) NOT NULL,
  reason          VARCHAR(255) NOT NULL,
  created_by      VARCHAR(150) NOT NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  CONSTRAINT chk_unavail_time_order CHECK (start_datetime < end_datetime),
  INDEX idx_unavail_range (provider_id, start_datetime, end_datetime)
) ENGINE=InnoDB;
