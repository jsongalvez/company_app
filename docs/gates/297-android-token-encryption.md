# Gates - 297: Android token encryption

- [x] G1: Android token storage has no ordinary SharedPreferences fallback
  CHECK: if grep -q 'context.getSharedPreferences' composeApp/src/androidMain/kotlin/com/companyb/companyapp/network/TokenStore.android.kt; then exit 1; else grep -q 'EncryptedSharedPreferences.create' composeApp/src/androidMain/kotlin/com/companyb/companyapp/network/TokenStore.android.kt; fi
  EXPECT: EXIT 0
  EVIDENCE: exit 0

- [x] G2: Encryption failure retains only process-memory token state
  CHECK: grep -q '@Volatile' composeApp/src/androidMain/kotlin/com/companyb/companyapp/network/TokenStore.android.kt && grep -q 'private var sessionToken' composeApp/src/androidMain/kotlin/com/companyb/companyapp/network/TokenStore.android.kt && grep -q 'prefs: SharedPreferences?' composeApp/src/androidMain/kotlin/com/companyb/companyapp/network/TokenStore.android.kt && grep -q 'Encrypted preferences unavailable' composeApp/src/androidMain/kotlin/com/companyb/companyapp/network/TokenStore.android.kt
  EXPECT: EXIT 0
  EVIDENCE: exit 0
