Part of #180

## Question

Make `tests/k6/remittance-race-test.js` fail closed on fixture setup failure and measure only concurrent remittance submission against a confirmed valid Remittance.

## Acceptance

- Validate login, Branch, Client, Session, and Remittance responses; abort setup on any non-success or missing required ID.
- Use Asia/Manila date for the Remittance range.
- Return only the confirmed Remittance ID.
- Keep concurrent submission result checks and disposable test-database cleanup intact.
- Add deterministic setup-failure validation and run k6 inspection/live race validation.
