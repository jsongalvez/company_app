# ComposeApp — NavHost-based routing

The composeApp currently uses a private `Screen` sealed class inside `HomeScreen.kt` for sub-navigation (Home, ClientSearch, SessionCreate), with state-based `when` branching. With 10+ screens coming (session detail, inventory, product sale, daily finance, remittance, and multi-level nesting), this pattern hits its ceiling: no back-stack, no deep linking, manual ViewModel lifecycle management per screen branch.

**Decision:** Adopt Compose Multiplatform NavHost with typed route definitions and argument passing. Replace the sealed class approach.

**Trade-off:** NavHost adds boilerplate (route sealed class, `NavController`, argument parsing) but provides a proper back-stack, centralized route definitions, ViewModel scoping per back-stack entry, and room for 3+ screen depth without the `HomeScreen` becoming unmaintainable.

**Considered:** Keeping the sealed class approach and extending it. Rejected because the screen count and nesting depth (Home → Clients → SessionCreate → SessionDetail; Home → Inventory → ProductSale; Home → Finance → Remittance → Draft) would make `HomeScreen.kt` a single-file monolith with tangled state management.
