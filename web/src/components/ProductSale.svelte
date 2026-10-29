<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { app } from "../lib/app.svelte";
    import { fetchCached, invalidate, peek } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { askReason } from "../lib/ask.svelte";
    import { Action } from "../lib/act.svelte";
    import { peso, shortDay } from "../lib/format";
    import Dialog from "./Dialog.svelte";

    let {
        open = $bindable(false),
        onsaved,
        sessionId = null,
        day = null,
        dayId = null,
        sale = null,
        needReason = false,
    }: {
        open?: boolean;
        onsaved: () => void;
        sessionId?: string | null;
        /** An earlier day to add the sale to; null is today. */
        day?: { id: string; date: string } | null;
        /** The day row to add to when the caller knows it but not its date (a session's own day). */
        dayId?: string | null;
        /** The sale to change; null adds a new one. */
        sale?: S["ProductSaleResponse"] | null;
        /** An earlier day: a change asks for its reason. */
        needReason?: boolean;
    } = $props();

    const a = new Action();
    let inv = $state<S["BranchInventoryResponse"][]>([]);
    let sessions = $state<S["DashboardSessionResponse"][]>([]);
    let productId = $state("");
    let qty = $state(1);
    let linkSession = $state("");

    $effect(() => {
        if (!open) return;
        a.reset();
        productId = sale?.productId ?? "";
        qty = sale?.quantity ?? 1;
        linkSession = sessionId ?? "";
        // Show what this phone last saw at once; fresh numbers replace it when the server answers.
        const l = load.inventory(app.branchId);
        // A sale being changed keeps its own product on the list even when none is left.
        const inStock = (r: S["BranchInventoryResponse"][]) => (inv = r.filter((i) => i.currentStock > 0 || i.productId === sale?.productId));
        const seen = peek<S["BranchInventoryResponse"][]>(l.key);
        if (seen) inStock(seen);
        fetchCached(l.key, l.fn, true).then(inStock, () => {});
        const live = (r: S["DashboardSessionResponse"][]) => (sessions = r.filter((s) => !s.isVoided));
        if (day) {
            const d = load.daySessions(app.branchId, day.date);
            const seen = peek<S["DashboardSessionResponse"][]>(d.key);
            if (seen) live(seen);
            fetchCached(d.key, d.fn).then(live, () => {});
        } else {
            const d = load.dashboard(app.branchId);
            const seen = peek<S["DashboardResponse"]>(d.key);
            if (seen) live(seen.sessions);
            fetchCached(d.key, d.fn).then(
                (r) => live(r.sessions),
                () => {},
            );
        }
    });

    const item = $derived(inv.find((i) => i.productId === productId));
    // A changed sale keeps its own price for its own product; another product takes the branch price.
    const unit = $derived(sale && productId === sale.productId ? Number(sale.unitPriceAtTime) : Number(item?.unitPrice ?? 0));
    const total = $derived(unit * qty);
    /** Units that can go on this sale: what is in stock, plus what the sale already holds of it. */
    const most = $derived((item?.currentStock ?? 999) + (sale && productId === sale.productId ? sale.quantity : 0));

    function refreshAfter() {
        invalidate(`inv:${app.branchId}`, `dash:${app.branchId}`, "dsess:", "dsum:", "moves:", "sales:", "audit:product_sale:");
        open = false;
        onsaved();
    }

    async function save() {
        if (sale) return change(sale);
        await a.run(async () => {
            if (!item) throw new Error("Choose a product");
            const target = day ? day.id : (dayId ?? app.today?.branchDayId);
            if (!target) throw new Error("Today isn't open at this branch yet. Try again in a moment.");
            // Opened from a session's page, the sale is that session's, on whatever day it was.
            const linked = sessionId ?? sessions.find((x) => x.id === linkSession)?.id ?? null;
            await ok(
                api.POST("/api/product-sales", {
                    body: {
                        id: uuid(),
                        branchDayId: target,
                        sessionId: linked,
                        clientId: null,
                        isWalkIn: !linked,
                        productId: item.productId,
                        quantity: qty,
                    },
                }),
            );
            refreshAfter();
        }, "Sale recorded");
    }

    async function change(was: S["ProductSaleResponse"]) {
        const reason = needReason ? await askReason("Change sale") : "";
        if (reason === null) return;
        await a.run(async () => {
            if (!item) throw new Error("Choose a product");
            await ok(
                api.PATCH("/api/product-sales/{saleId}", {
                    params: { path: { saleId: was.id } },
                    body: { productId: item.productId, quantity: qty, expectedVersion: was.version, reason: reason || null },
                }),
            );
            refreshAfter();
        }, "Sale changed");
    }
</script>

<Dialog
    bind:open
    title={sale ? "Change sale" : day ? `Product sale on ${shortDay(day.date)}` : "Product sale"}
    onsubmit={save}
    submitText={sale ? "Save" : "Record sale"}
    busy={a.busy}
    error={a.error}
>
    <div class="form-grid">
        <label class="field wide"
            ><span class="req">Product</span>
            <select bind:value={productId} required>
                <option value="" disabled>Select a product in stock</option>
                {#each inv as i (i.id)}<option value={i.productId}
                        >{i.productName} · {peso(sale && i.productId === sale.productId ? sale.unitPriceAtTime : i.unitPrice)} · {i.currentStock} left</option
                    >{/each}
            </select>
        </label>
        <label class="field"><span class="req">Quantity</span><input class="num" type="number" min="1" max={most} bind:value={qty} required /></label>
        <label class="field">Total<input class="num" value={peso(total)} disabled /></label>
        {#if !sale && !sessionId}<label class="field wide"
                >Sold during session
                <select bind:value={linkSession}>
                    <option value="">None: walk-in sale</option>
                    {#each sessions as s (s.id)}<option value={s.id}>{s.clientName ?? "(details erased)"}</option>{/each}
                </select>
            </label>{/if}
    </div>
</Dialog>
