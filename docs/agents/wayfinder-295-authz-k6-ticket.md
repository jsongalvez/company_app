# Build: centralize authz k6 thresholds

Part of Map #180.

## Question

Move authz workflow thresholds into `tests/k6/helpers.js` as named `thresholdProfiles.authz`, preserving current latency and error limits. Make `authz-test.js` consume that profile and validate it with `k6 inspect` or equivalent deterministic fixture coverage.
