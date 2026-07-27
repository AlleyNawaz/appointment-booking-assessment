CREATE TABLE shedlock (
  name        VARCHAR(64) NOT NULL PRIMARY KEY,
  lock_until  DATETIME(3) NOT NULL,
  locked_at   DATETIME(3) NOT NULL,
  locked_by   VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
