+Part of #180
+
+## Question
+
+Can identical Android and iOS mobile UI-part implementations move to `commonMain` while preserving existing `expect`/`actual` seams and distinct Desktop behavior?
+
+## Scope
+
+Move the six identical mobile implementation bodies for Client, Audit Log, Finance Day Detail, Remittance, User Management, and Dashboard Empty State into `commonMain`. Leave thin Android/iOS delegates and Desktop implementations unchanged. Preserve signatures, behavior, and unused compatibility parameters. Validate common, Desktop, Android, and iOS compilation where dependency availability permits.
