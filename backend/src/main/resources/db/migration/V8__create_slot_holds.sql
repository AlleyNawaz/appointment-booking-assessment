CREATE TABLE slot_holds (
  id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  provider_id          BIGINT UNSIGNED NOT NULL,
  appointment_type_id  BIGINT UNSIGNED NOT NULL,
  start_datetime       DATETIME(3) NOT NULL,
  end_datetime         DATETIME(3) NOT NULL,
  hold_token           CHAR(36) NOT NULL UNIQUE,
  expires_at           DATETIME(3) NOT NULL,
  created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  FOREIGN KEY (appointment_type_id) REFERENCES appointment_types(id),
  UNIQUE KEY uq_hold_slot (provider_id, start_datetime)
) ENGINE=InnoDB;
