<script lang="ts">
    import { viewSetting } from "../lib/router.svelte";
    import { api, ok, uuid, type S } from "../api/client";
    import { app, can, canAtBranch } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { peso, num, time, dateTime, label, operationalDate } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";
    import ProductSale from "../components/ProductSale.svelte";

    const bid = $derived(app.branchId);
    const inv = new Query(load.inventory(app.branchId).key, load.inventory(app.branchId).fn);
    // No day picked: the latest changes across all days.
    const dayVs = viewSetting("date", "");
    let date = $state(dayVs.read);
    $effect(() => dayVs.keep(date));
    const moveVs = viewSetting("product", "");
    let moveProduct = $state(moveVs.read);
    $effect(() => moveVs.keep(moveProduct));
    const moves = new Query(load.movements(app.branchId, "").key, load.movements(app.branchId, "").fn);
    $effect(() => {
        const l = load.inventory(bid);
        inv.load(l.key, l.fn);
    });
    $effect(() => {
        const l = load.movements(bid, date);
        moves.load(l.key, l.fn);
        older = [];
        noMore = false;
    });

    // "Show older": the server sends the latest 200 at a time; each tap fetches the next 200.
    const PAGE = 200;
    let older = $state<S["InventoryMovementResponse"][]>([]);
    let noMore = $state(false);
    let olderBusy = $state(false);
    let olderErr = $state("");
    const allMoves = $derived([...(moves.data ?? []), ...older]);
    const canShowOlder = $derived(!date && !noMore && allMoves.length >= PAGE && allMoves.length % PAGE === 0);
    async function showOlder() {
        const last = allMoves[allMoves.length - 1];
        if (!last) return;
        olderBusy = true;
        olderErr = "";
        try {
            const more = await ok(
                api.GET("/api/branches/{branchId}/inventory/movements", { params: { path: { branchId: bid }, query: { before: last.movedAt } } }),
            );
            older = [...older, ...more];
            if (more.length < PAGE) noMore = true;
        } catch (e) {
            olderErr = e instanceof Error ? e.message : String(e);
        } finally {
            olderBusy = false;
        }
    }

    const edit = $derived(can("EDIT_BRANCH_DATA"));
    const manage = $derived(canAtBranch("MANAGE_PRODUCTS"));
    const LOW = 5;
    const nameVs = viewSetting("q", "");
    let filter = $state(nameVs.read);
    $effect(() => nameVs.keep(filter));
    const rows = $derived((inv.data ?? []).filter((i) => !filter || i.productName.toLowerCase().includes(filter.toLowerCase())));
    const shownMoves = $derived(allMoves.filter((m) => !moveProduct || m.productId === moveProduct));
    const names = $derived(Object.fromEntries((inv.data ?? []).map((i) => [i.productId, i.productName])));

    function refresh() {
        invalidate(`inv:${bid}`, `moves:${bid}`);
        inv.refresh();
        moves.refresh();
        older = [];
        noMore = false;
    }

    // Restock / adjust share one dialog.
    const a = new Action();
    let open = $state(false);
    let mode = $state<"restock" | "adjust">("restock");
    let item = $state<S["BranchInventoryResponse"] | null>(null);
    let qty = $state(1);
    let reason = $state<"TESTER" | "SAMPLE" | "MISSING" | "ADJUSTMENT">("ADJUSTMENT");
    let direction = $state<-1 | 1>(-1);
    let notes = $state("");
    let editReason = $state("");

    function openFor(i: S["BranchInventoryResponse"], m: "restock" | "adjust") {
        item = i;
        mode = m;
        qty = 1;
        reason = "ADJUSTMENT";
        direction = -1;
        notes = editReason = "";
        a.reset();
        open = true;
    }

    async function save() {
        const i = item;
        if (!i) return;
        await a.run(
            async () => {
                if (!app.today) throw new Error("Today isn't open at this branch yet. Try again in a moment.");
                const dayId = app.today.branchDayId;
                const er = editReason.trim() || null;
                if (mode === "restock") {
                    await ok(
                        api.POST("/api/branches/{branchId}/inventory/{productId}/restock", {
                            params: { path: { branchId: bid, productId: i.productId } },
                            body: { id: uuid(), quantity: qty, branchDayId: dayId, editReason: er },
                        }),
                    );
                } else {
                    const change = reason === "ADJUSTMENT" ? direction * qty : -qty;
                    await ok(
                        api.POST("/api/branches/{branchId}/inventory/{productId}/movement", {
                            params: { path: { branchId: bid, productId: i.productId } },
                            body: {
                                movementId: uuid(),
                                reason,
                                quantityChange: change,
                                notes: notes.trim() || null,
                                branchDayId: dayId,
                                expectedVersion: i.version,
                                editReason: er,
                            },
                        }),
                    );
                }
                open = false;
                refresh();
            },
            mode === "restock" ? "Restocked" : "Stock count fixed",
        );
    }

    // Add product to this branch's stock list.
    const aa = new Action();
    let addOpen = $state(false);
    let products = $state<S["ProductResponse"][]>([]);
    let addId = $state("");
    async function openAdd() {
        aa.reset();
        addId = "";
        addOpen = true;
        const l = load.products();
        const all = await fetchCached(l.key, l.fn).catch(() => []);
        const have = new Set((inv.data ?? []).map((i) => i.productId));
        products = all.filter((p) => p.isActive && !have.has(p.id));
    }
    async function add() {
        if (
            await aa.run(
                () => ok(api.POST("/api/branches/{branchId}/inventory", { params: { path: { branchId: bid } }, body: { productId: addId } })),
                "Product added",
            )
        ) {
            addOpen = false;
            refresh();
        }
    }

    let saleOpen = $state(false);
    const totalValue = $derived((inv.data ?? []).reduce((s, i) => s + Number(i.unitPrice) * i.currentStock, 0));
</script>

<PageHead title="Stock">
    <input type="search" placeholder="Filter products" bind:value={filter} />
    {#if manage}<button onclick={openAdd}>Add product</button>{/if}
    {#if edit}<button class="primary" onclick={() => (saleOpen = true)}>Product sale</button>{/if}
</PageHead>

<State q={inv} empty={!!inv.data && inv.data.length === 0} emptyText="This branch has no products in stock yet." />
{#if rows.length}
    <div class="table-wrap mb">
        <table class="grid">
            <thead>
                <tr>
                    <th>Product</th>
                    <th class="num">Price</th>
                    <th class="num">Commission</th>
                    <th class="num">In stock</th>
                    <th class="num" title="Today">Sold</th>
                    <th class="num" title="Today">Tester/sample</th>
                    <th class="num" title="Today">Missing</th>
                    <th class="num" title="Today">Count fixes</th>
                    {#if edit}<th></th>{/if}
                </tr>
            </thead>
            <tbody>
                {#each rows as i (i.id)}
                    <tr>
                        <td>{i.productName}</td>
                        <td class="num">{peso(i.unitPrice)}</td>
                        <td class="num muted">{peso(i.commissionAmount)}</td>
                        <td class="num">
                            <b class:low={i.currentStock <= LOW}>{num(i.currentStock)}</b>
                            {#if i.currentStock === 0}<span class="mark bad">Out</span>{:else if i.currentStock <= LOW}<span class="mark warn">Low</span>{/if}
                        </td>
                        <td class="num">{num(i.sales)}</td>
                        <td class="num">{num(i.testerSample)}</td>
                        <td class="num">{num(i.missing)}</td>
                        <td class="num">{num(i.adjustment)}</td>
                        {#if edit}
                            <td class="right nowrap">
                                <button class="sm" onclick={() => openFor(i, "restock")}>Restock</button>
                                <button class="sm" onclick={() => openFor(i, "adjust")}>Fix count</button>
                            </td>
                        {/if}
                    </tr>
                {/each}
            </tbody>
            <tfoot>
                <tr
                    ><td colspan="3">{rows.length} products</td><td class="num">{num(rows.reduce((s, i) => s + i.currentStock, 0))}</td><td
                        colspan={edit ? 5 : 4}
                        class="muted">Stock value {peso(totalValue)}</td
                    ></tr
                >
            </tfoot>
        </table>
    </div>
{/if}

<section class="panel">
    <header>
        <h2>Stock changes</h2>
        <span class="spacer"></span>
        <select bind:value={moveProduct} aria-label="Product">
            <option value="">All products</option>
            {#each inv.data ?? [] as i (i.productId)}<option value={i.productId}>{i.productName}</option>{/each}
        </select>
        <input type="date" bind:value={date} max={operationalDate()} aria-label="Day" />
        {#if date}<button type="button" onclick={() => (date = "")}>Latest</button>{/if}
    </header>
    <State q={moves} empty={!!moves.data && shownMoves.length === 0} emptyText={date ? "No stock changes on this day." : "No stock changes yet."} />
    {#if shownMoves.length}
        <table class="grid">
            <thead><tr><th>{date ? "Time" : "When"}</th><th>Product</th><th>Type</th><th class="num">Change</th><th>Notes</th></tr></thead>
            <tbody>
                {#each shownMoves as m (m.id)}
                    <tr>
                        <td>{date ? time(m.movedAt) : dateTime(m.movedAt)}</td>
                        <td>{names[m.productId] ?? ""}</td>
                        <td>{label(m.reason)}</td>
                        <td class="num" class:neg={m.quantityChange < 0}>{m.quantityChange > 0 ? "+" : ""}{m.quantityChange}</td>
                        <td class="wrap muted">{m.notes ?? ""}</td>
                    </tr>
                {/each}
            </tbody>
        </table>
    {/if}
    {#if canShowOlder}
        <div class="more">
            <button type="button" onclick={showOlder} disabled={olderBusy}>{olderBusy ? "Loading…" : "Show older"}</button>
            {#if olderErr}<span class="notice bad">{olderErr}</span>{/if}
        </div>
    {/if}
</section>

<Dialog
    bind:open
    title={mode === "restock" ? `Restock: ${item?.productName}` : `Fix count: ${item?.productName}`}
    width={400}
    onsubmit={save}
    submitText={mode === "restock" ? "Restock" : "Save"}
    busy={a.busy}
    error={a.error}
>
    <div class="muted">In stock now: <b>{item?.currentStock}</b></div>
    <div class="form-grid">
        {#if mode === "adjust"}
            <label class="field"
                >Type
                <select bind:value={reason}>
                    <option value="TESTER">Tester (out)</option>
                    <option value="SAMPLE">Sample (out)</option>
                    <option value="MISSING">Missing (out)</option>
                    <option value="ADJUSTMENT">Count fix</option>
                </select>
            </label>
            {#if reason === "ADJUSTMENT"}
                <label class="field"
                    >Direction
                    <select bind:value={direction}>
                        <option value={-1}>Remove</option>
                        <option value={1}>Add</option>
                    </select>
                </label>
            {/if}
        {/if}
        <label class="field"><span class="req">Quantity</span><input class="num" type="number" min="1" bind:value={qty} required /></label>
        {#if mode === "adjust"}<label class="field wide">Notes<input bind:value={notes} /></label>{/if}
        {#if app.today?.status !== "OPEN"}<label class="field wide">Reason for edit<input bind:value={editReason} /></label>{/if}
    </div>
</Dialog>

<Dialog bind:open={addOpen} title="Add product to this branch" width={400} onsubmit={add} submitText="Add" busy={aa.busy || !addId} error={aa.error}>
    <label class="field"
        >Product
        <select bind:value={addId} required>
            <option value="" disabled>Select</option>
            {#each products as p (p.id)}<option value={p.id}>{p.name} · {peso(p.unitPrice)}</option>{/each}
        </select>
    </label>
    {#if products.length === 0}<div class="faint">All products stocked.</div>{/if}
</Dialog>

<ProductSale bind:open={saleOpen} onsaved={refresh} />

<style>
    .more {
        display: flex;
        gap: 8px;
        align-items: center;
        padding: 8px 0;
    }
    .mb {
        margin-bottom: 12px;
    }
    .low {
        color: var(--warn);
    }
    .neg {
        color: var(--bad);
    }
</style>
