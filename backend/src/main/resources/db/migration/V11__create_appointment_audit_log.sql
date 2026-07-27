CREATE TABLE appointment_audit_log (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  appointment_id   BIGINT UNSIGNED NOT NULL,
  previous_status  VARCHAR(20) NULL,
  new_status       VARCHAR(20) NOT NULL,
  changed_by       VARCHAR(150) NOT NULL,
  reason           VARCHAR(255) NULL,
  changed_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (appointment_id) REFERENCES appointments(id)
) ENGINE=InnoDB;
