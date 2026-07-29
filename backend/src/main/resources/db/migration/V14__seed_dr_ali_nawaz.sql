INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) VALUES ('Ali', 'Nawaz', 'General Practitioner', 'ali@example.com', 'America/New_York', TRUE);

SET @provider_id = LAST_INSERT_ID();

INSERT INTO provider_appointment_types (provider_id, appointment_type_id)
SELECT @provider_id, id FROM appointment_types;

INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type) VALUES 
(@provider_id, 1, '09:00:00', '17:00:00', 'WORKING'),
(@provider_id, 2, '09:00:00', '17:00:00', 'WORKING'),
(@provider_id, 3, '09:00:00', '17:00:00', 'WORKING'),
(@provider_id, 4, '09:00:00', '17:00:00', 'WORKING'),
(@provider_id, 5, '09:00:00', '17:00:00', 'WORKING');
