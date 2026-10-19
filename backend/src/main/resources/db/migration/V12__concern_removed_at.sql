-- A bad illness entry can be taken off the shared list. Past sessions keep showing it.
ALTER TABLE concern ADD COLUMN removed_at TIMESTAMPTZ;
