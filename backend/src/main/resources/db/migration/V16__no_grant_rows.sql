-- Rights come only from facts (roles, assignments, relief days, helper seats), so grant rows go.
-- The rows left are view, edit and void at a branch the holder is assigned to, which a Practitioner
-- member holds: a holder with no role becomes one. Any other row stops the migration before a right is lost.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_capability uc
        JOIN capability c ON c.id = uc.capability_id
        WHERE c.code NOT IN ('VIEW_BRANCH_DATA', 'EDIT_BRANCH_DATA', 'VOID_SESSION')
           OR uc.context_type <> 'BRANCH'
           OR NOT EXISTS (
               SELECT 1 FROM user_branch_assignment a
               WHERE a.user_id = uc.user_id AND a.branch_id = uc.context_id AND a.ended_at IS NULL
           )
    ) THEN
        RAISE EXCEPTION 'user_capability holds a row no fact stands for';
    END IF;
END $$;

INSERT INTO user_role (user_id, role_id)
SELECT DISTINCT uc.user_id, r.id
FROM user_capability uc, role r
WHERE r.name = 'PRACTITIONER'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = uc.user_id);

DROP TABLE user_capability;
DROP TABLE capability;
DROP TYPE capability_source_type;
DROP TYPE capability_context_type;
