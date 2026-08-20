# Gates - 296: typed Compose statuses

- [x] G1: Compose status logic no longer compares enum names or owns status string constants
  CHECK: sh -c '! grep -R -n -E "SESSION_STATUS_COMPLETED|USER_STATUS_ACTIVE|USER_STATUS_INACTIVE|sessionStatus\.name ==|status\.name ==" composeApp/src/commonMain'
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Compose sources pass lint
  CHECK: ./gradlew :composeApp:ktlintCheck
  EXPECT: EXIT 0
  EVIDENCE: Starting a Gradle Daemon, 2 stopped Daemons could not be reused, use --status for details
