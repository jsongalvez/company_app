-- Visits a client had before the app existed. Counted toward visit history,
-- so a returning client gets the right visit type (second visit, return visit).
ALTER TABLE client
    ADD COLUMN earlier_visits integer NOT NULL DEFAULT 0
        CONSTRAINT client_earlier_visits_range CHECK (earlier_visits BETWEEN 0 AND 1000);
