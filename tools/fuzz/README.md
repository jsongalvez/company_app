# Fuzz the API

Throws random and boundary inputs at every route in the OpenAPI contract with
[Schemathesis](https://schemathesis.readthedocs.io), once per seeded role
(owner, staff, relief, superuser) and once without a token.

**Pass** means no 5xx and every response matches its documented schema. A
failure prints a `curl` repro and a `st replay <id>` command in `build/fuzz/<role>.log`.

## Run it

Start a backend on an empty database with the dev logins (the same env as
`.github/workflows/browser.yml`), then:

    FUZZ_URL=http://localhost:18080 tools/fuzz/run.sh      # about 2 minutes
    FUZZ_URL=... FUZZ_EXAMPLES=30 tools/fuzz/run.sh        # deeper, slower
    FUZZ_SEED=7 tools/fuzz/run.sh                          # another random draw

`psql` must reach the same database (`PGHOST`, `PGUSER`, ...). `uvx` or a
`schemathesis==4.32.3` install must be on the path.

## How it works

1. `seed.py` creates a client, product, inventory card, clock-in, session, expense and
   remittance through the real API and adds a throwaway user, then writes the ids of existing rows to
   `build/fuzz/params.toml`. Without real ids nearly every request would 404 before reaching a handler.
2. `run.sh` logs in as each role and runs the five Schemathesis passes in parallel.
   The owner pass also runs the boundary-case phase.

## Limits

- Stateless. The contract has no links between operations, so a route that needs a row created by another
  route in the same run (relief invites, bookings, compensation, delegates, remittance lines) is reached only
  through whatever the seeder made. Those routes mostly answer 404.
- The generator rarely produces a body the validators accept, so most responses are 400. It finds crashes
  on the way to validation, not wrong business results.
- `POST /api/me/password` and everything under `/auth` are skipped: they rotate the logins the run depends on
  or hit the login rate limit.
- Run it on a fresh database. Earlier runs leave rows and a re-run may revoke the seeded logins.
- It checks status codes and schemas, not data invariants such as totals or ownership.
