## Question

How should Android token storage behave when `EncryptedSharedPreferences` cannot be created? Preserve bearer-token confidentiality by failing closed instead of silently writing to ordinary preferences. Verify re-authentication recovery and avoid migration assumptions for any existing plaintext fallback data.
