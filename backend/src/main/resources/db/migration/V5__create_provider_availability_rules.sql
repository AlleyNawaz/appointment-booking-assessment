CREATE TABLE provider_availability_rules (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  provider_id  BIGINT UNSIGNED NOT NULL,
  day_of_week  TINYINT UNSIGNED NOT NULL,  -- 0=Sunday .. 6=Saturday
  start_time   TIME NOT NULL,
  end_time     TIME NOT NULL,
  rule_type    ENUM('WORKING','BREAK') NOT NULL DEFAULT 'WORKING',
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  CONSTRAINT chk_rule_time_order CHECK (start_time < end_time)
) ENGINE=InnoDB;
