CREATE TABLE clinic_holidays (
  id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  holiday_date           DATE NOT NULL UNIQUE,
  name                   VARCHAR(150) NOT NULL,
  is_recurring_annually  BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
