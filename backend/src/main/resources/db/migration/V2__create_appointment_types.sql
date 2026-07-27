CREATE TABLE appointment_types (
  id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code               VARCHAR(50)  NOT NULL UNIQUE,
  display_name       VARCHAR(150) NOT NULL,
  duration_minutes   SMALLINT UNSIGNED NOT NULL,
  buffer_minutes     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  requires_approval  BOOLEAN NOT NULL DEFAULT FALSE,
  is_active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

INSERT INTO appointment_types (code, display_name, duration_minutes, buffer_minutes, requires_approval) VALUES
  ('NEW_PATIENT',        'New Patient Intake',      45, 15, TRUE),
  ('GENERAL_CONSULT',    'General Consultation',    30, 0,  FALSE),
  ('FOLLOW_UP',          'Follow-Up',               15, 0,  FALSE),
  ('SPECIALIST_CONSULT', 'Specialist Consultation', 60, 15, TRUE);
