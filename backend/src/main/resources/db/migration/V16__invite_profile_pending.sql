-- An invite minted without name, username and email: the account holds placeholders
-- until the invitee fills in their own when they accept.
ALTER TABLE app_user ADD COLUMN profile_pending BOOLEAN NOT NULL DEFAULT false;
