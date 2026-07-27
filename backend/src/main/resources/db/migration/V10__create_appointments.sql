CREATE TABLE appointments (
  id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  confirmation_token   CHAR(36) NOT NULL UNIQUE,
  provider_id          BIGINT UNSIGNED NOT NULL,
  appointment_type_id  BIGINT UNSIGNED NOT NULL,
  patient_full_name    VARCHAR(100) NOT NULL,
  patient_email        VARCHAR(254) NOT NULL,
  patient_phone        VARCHAR(20)  NOT NULL,
  notes                VARCHAR(500) NULL,
  start_datetime       DATETIME(3) NOT NULL,
  end_datetime         DATETIME(3) NOT NULL,
  status               ENUM('PENDING','CONFIRMED','CANCELLED','COMPLETED','REJECTED','EXPIRED','MISSED')
                       NOT NULL DEFAULT 'CONFIRMED',
  cancellation_reason  VARCHAR(255) NULL,
  idempotency_key      CHAR(36) NOT NULL,
  request_body_hash    CHAR(64) NOT NULL,
  active_slot_key      VARCHAR(80) GENERATED ALWAYS AS (
                          CASE WHEN status IN ('PENDING','CONFIRMED')
                               THEN CONCAT(provider_id, '_', DATE_FORMAT(start_datetime, '%Y%m%d%H%i%s'))
                               ELSE NULL END
                        ) STORED,
  version              INT UNSIGNED NOT NULL DEFAULT 0,
  created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at           DATETIME(3) NULL,
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  FOREIGN KEY (appointment_type_id) REFERENCES appointment_types(id),
  UNIQUE KEY uq_active_slot   (active_slot_key),
  UNIQUE KEY uq_idempotency   (idempotency_key),
  INDEX idx_patient_lookup    (patient_email, patient_phone, start_datetime),
  INDEX idx_provider_time     (provider_id, start_datetime),
  INDEX idx_status            (status)
) ENGINE=InnoDB;
