-- A signed-out page (the sign-in screen) uploads its errors too, with no user.
ALTER TABLE telemetry_event ALTER COLUMN user_id DROP NOT NULL;
