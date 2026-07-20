# ADR-0018: Lambda-based entity audit overloads instead of Auditable interface

**Status:** Accepted  
**Date:** 2026-07-20

Entity-based `recordUpdate<T>` and `recordDelete<T>` overloads use a `auditFields: (T) -> Map<String, String>` lambda parameter rather than requiring Table objects to implement an `Auditable<T>` interface. An interface would make the type relationship explicit at the cost of modifying every Table object (21 files). The lambda approach keeps the `auditFields()` function on the Table companion (already the single source of truth per ADR-0014) without changing the Table's type hierarchy. Call sites pass `TableName::auditFields` as a method reference.
