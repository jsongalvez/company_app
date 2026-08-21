+Part of #180
+
+## Question
+
+Can remaining finite backend persistence enums use the existing shared Kotlin enum ownership without changing PostgreSQL or wire values?
+
+## Scope
+
+Migrate remittance, expense, branch-day, and user-status persistence models and direct consumers to shared `WireEnums` values. Preserve `customEnumeration` PostgreSQL bindings, uppercase serialization, validation behavior, and no migration changes. Add focused tests for serialization, persistence, and invalid values.
