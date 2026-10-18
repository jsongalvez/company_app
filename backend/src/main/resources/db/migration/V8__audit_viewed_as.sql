-- Developer "View as" (feedback 378fdb6d): when a superuser acts while viewing as someone,
-- the history row keeps the person whose permissions were used in changed_by and the
-- superuser in viewed_as_by.
ALTER TABLE audit_log ADD COLUMN viewed_as_by UUID REFERENCES app_user(id);
