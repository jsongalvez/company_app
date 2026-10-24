# Patterns log

Log first, fix later. Agents add a row when the same kind of mistake shows up more than once
(feedback reports, fix commits, review findings, corrections in chat). Nobody fixes a row from here.
The user reads the log and approves rows; an approved row becomes a rule, type, lint or test that makes
the mistake impossible (see `/correct`), and its row moves to "Done" with the commit that did it.

This repo is public: describe the pattern, never quote a person, a name or a message.

## Open

| Pattern | Seen in (commits, feedback ids) | Count | Possible lever (not built) | Approved? |
|---|---|---|---|---|

## Cleanup candidates (gardening)

Code that makes the next change harder: giant files, duplicate helpers, dead flags. Logged, not fixed.

| Where | Why it slows work | Size |
|---|---|---|

## Done

| Pattern | Lever | Commit |
|---|---|---|
