-- A hidden branch (the latency probe's) is left out of every branch list, the all-branches export and a global viewer's audit.
ALTER TABLE branch ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT false;
