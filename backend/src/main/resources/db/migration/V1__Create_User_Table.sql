CREATE TABLE if NOT EXISTS app_user(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL,
    password_hash CHAR(60) NOT NULL
);
