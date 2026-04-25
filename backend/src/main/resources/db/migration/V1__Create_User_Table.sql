CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE');

CREATE TABLE if NOT EXISTS app_user(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL DEFAULT 'User'
);
