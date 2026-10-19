-- TMJ is enough: drop the long form. If a plain "TMJ" already exists, move the sessions over and drop the long one.
INSERT INTO session_concern (session_id, concern_id)
SELECT sc.session_id, t.id
FROM session_concern sc
JOIN concern l ON l.id = sc.concern_id AND l.label = 'TMJ (Temporo-Mandibular Joint)'
JOIN concern t ON t.label = 'TMJ'
ON CONFLICT DO NOTHING;

DELETE FROM session_concern
WHERE concern_id IN (SELECT id FROM concern WHERE label = 'TMJ (Temporo-Mandibular Joint)')
  AND EXISTS (SELECT 1 FROM concern WHERE label = 'TMJ');

DELETE FROM concern
WHERE label = 'TMJ (Temporo-Mandibular Joint)'
  AND EXISTS (SELECT 1 FROM concern WHERE label = 'TMJ');

UPDATE concern SET label = 'TMJ' WHERE label = 'TMJ (Temporo-Mandibular Joint)';
