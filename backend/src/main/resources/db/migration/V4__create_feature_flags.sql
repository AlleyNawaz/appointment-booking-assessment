CREATE TABLE feature_flags (
  flag_name   VARCHAR(100) PRIMARY KEY,
  is_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
  updated_by  VARCHAR(150) NOT NULL,
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

INSERT INTO feature_flags (flag_name, is_enabled, updated_by)
VALUES ('enable_online_booking', FALSE, 'SYSTEM_SEED');
