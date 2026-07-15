# 13 — Inventory + product sale screens

**What to build:** Inventory screen showing products grouped by category with stock levels, low-stock highlights, and restock actions. Product sale screen for recording sales (in-session or out-of-session). Reuses existing `InventoryViewModel` and `ProductSaleViewModel`.

**Blocked by:** 11 — Navigation drawer

**Status:** ready-for-agent

- [ ] `InventoryScreen.kt`: products grouped by category in expandable sections. Each product card shows name, unit price, commission, and branch stock (available/stock/sales/tester/sample/missing)
- [ ] Low-stock visual alert: zero or near-zero available stock highlighted with error accent
- [ ] Restock button per product → calls `POST /api/inventory/movement` with RESTOCK type
- [ ] `ProductSaleScreen.kt`: product dropdown selector, quantity input, optional session reference, total price display
- [ ] Sale submission: `POST /api/product-sales` with product, quantity, client (optional), session (optional)
- [ ] Out-of-session sale: session_id = null, must select a client
- [ ] Non-sale movements (tester, sample, missing): accessed from inventory screen
- [ ] ViewModel tests: inventory load, sale create, stock validation (insufficient stock error)
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:desktopTest` passes
