-- The most common concerns are always on the list, so a new session can pick them at once.
UPDATE concern SET label = upper(left(label, 1)) || substr(label, 2) WHERE label <> upper(left(label, 1)) || substr(label, 2);
UPDATE concern SET label = 'Loss of appetite' WHERE label = 'Loss of apetite';

INSERT INTO concern (label)
SELECT v.label
FROM (VALUES
    ('Knee pain'),
    ('Shoulder pain'),
    ('Lower back pain'),
    ('Back pain'),
    ('TMJ (Temporo-Mandibular Joint)'),
    ('Vertigo'),
    ('Hearing impaired')
) AS v(label)
WHERE NOT EXISTS (SELECT 1 FROM concern c WHERE lower(c.label) = lower(v.label));
