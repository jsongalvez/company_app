-- The role rules live in backend authorization/Policy.kt, which reads user_role, assignments
-- and user_capability rows directly. Nothing reads the view or the role seed any more.
DROP VIEW active_user_capabilities;
DROP TABLE role_capability;
