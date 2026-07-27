CREATE TABLE provider_appointment_types (
  provider_id          BIGINT UNSIGNED NOT NULL,
  appointment_type_id  BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (provider_id, appointment_type_id),
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  FOREIGN KEY (appointment_type_id) REFERENCES appointment_types(id)
) ENGINE=InnoDB;
