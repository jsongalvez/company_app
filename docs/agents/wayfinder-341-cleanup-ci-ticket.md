# Map #180 Candidate: CI Cleanup Script Coverage

## Question

Should CI run when `scripts/clean-test-db.sh` changes? `.github/workflows/quality.yml`
filters `scripts/check-test-cleanliness.sh` and `scripts/lib/**`, but omits the cleanup
script. The cleanup script is mandatory after k6 and has dedicated shell/disposable
database tests. Add the missing path to both push and pull-request filters, then run
the repository's shell and workflow path-policy checks. No runtime or database
behavior change.
