INSERT INTO status_code (code, label, detail) VALUES
    ('WEAK_PASSWORD', 'Weak Password', 'Password needs at least 8 characters'),
    ('USERNAME_TAKEN', 'Username Taken', 'This username is taken')
ON CONFLICT(code) DO UPDATE
    SET label = EXCLUDED.label,
        detail = EXCLUDED.detail;
