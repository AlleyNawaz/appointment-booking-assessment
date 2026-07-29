SET @sarah_id = (SELECT id FROM providers WHERE email = 'sarah.jenkins@clinic.com' LIMIT 1);
SET @michael_id = (SELECT id FROM providers WHERE email = 'michael.chen@clinic.com' LIMIT 1);

-- In case they don't exist
INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active)
SELECT 'Sarah', 'Jenkins', 'General Practice', 'sarah.jenkins@clinic.com', 'America/New_York', TRUE
WHERE @sarah_id IS NULL;

INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active)
SELECT 'Michael', 'Chen', 'Cardiology', 'michael.chen@clinic.com', 'America/New_York', TRUE
WHERE @michael_id IS NULL;

SET @sarah_id = (SELECT id FROM providers WHERE email = 'sarah.jenkins@clinic.com' LIMIT 1);
SET @michael_id = (SELECT id FROM providers WHERE email = 'michael.chen@clinic.com' LIMIT 1);

-- Clean up any existing mapping to prevent duplicates
DELETE FROM provider_appointment_types WHERE provider_id IN (@sarah_id, @michael_id);
DELETE FROM provider_availability_rules WHERE provider_id IN (@sarah_id, @michael_id);

INSERT INTO provider_appointment_types (provider_id, appointment_type_id)
VALUES 
  (@sarah_id, 1), (@sarah_id, 2), (@sarah_id, 3),
  (@michael_id, 3), (@michael_id, 4);

INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type)
VALUES 
  (@sarah_id, 1, '09:00:00', '17:00:00', 'WORKING'),
  (@sarah_id, 2, '09:00:00', '17:00:00', 'WORKING'),
  (@sarah_id, 3, '09:00:00', '17:00:00', 'WORKING'),
  (@sarah_id, 4, '09:00:00', '17:00:00', 'WORKING'),
  (@sarah_id, 5, '09:00:00', '17:00:00', 'WORKING'),
  
  (@michael_id, 1, '10:00:00', '16:00:00', 'WORKING'),
  (@michael_id, 2, '10:00:00', '16:00:00', 'WORKING'),
  (@michael_id, 3, '10:00:00', '16:00:00', 'WORKING'),
  (@michael_id, 4, '10:00:00', '16:00:00', 'WORKING'),
  (@michael_id, 5, '10:00:00', '16:00:00', 'WORKING');
