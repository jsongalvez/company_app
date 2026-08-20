# Map #180 Candidate: Correct Stale Test Database ADR Gate Wording

## Question

Should ADR-0006 describe current gate ownership? It says pre-push cleanliness runs
before local JMH, while current pre-push runs cleanliness before k6 and JMH runs in
CI. Update only the stale gate wording and verify references against `.githooks/pre-push`
and `.github/workflows/jmh.yml`. No runtime or database behavior change.
