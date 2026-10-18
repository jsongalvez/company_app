<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { app } from "../lib/app.svelte";
    import { fetchCached, invalidate, peek } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { peso } from "../lib/format";
    import Dialog from "./Dialog.svelte";

    let { open = $bindable(false), onsaved, sessionId = null }: { open?: boolean; onsaved: () => void; sessionId?: string | null } = $props();

    const a = new Action();
    let inv = $state<S["BranchInventoryResponse"][]>([]);
    let sessions = $state<S["DashboardSessionResponse"][]>([]);
    let productId = $state("");
    let qty = $state(1);
    let linkSession = $state("");

    $effect(() => {
        if (!open) return;
        a.reset();
        productId = "";
        qty = 1;
        linkSession = sessionId ?? "";
        // Show what this phone last saw at once; fresh numbers replace it when the server answers.
        const l = load.inventory(app.branchId);
        const inStock = (r: S["BranchInventoryResponse"][]) => (inv = r.filter((i) => i.currentStock > 0));
        const seen = peek<S["BranchInventoryResponse"][]>(l.key);
        if (seen) inStock(seen);
        fetchCached(l.key, l.fn, true).then(inStock, () => {});
        const d = load.dashboard(app.branchId);
        const live = (r: S["DashboardResponse"]) => (sessions = r.sessions.filter((s) => !s.isVoided));
        const day = peek<S["DashboardResponse"]>(d.key);
        if (day) live(day);
        fetchCached(d.key, d.fn).then(live, () => {});
    });

    const item = $derived(inv.find((i) => i.productId === productId));
    const total = $derived(item ? Number(item.unitPrice) * qty : 0);

    async function save() {
        await a.run(async () => {
            if (!item) throw new Error("Choose a product");
            if (!app.today) throw new Error("Today isn't open at this branch yet. Try again in a moment.");
            const s = sessions.find((x) => x.id === linkSession);
            await ok(
                api.POST("/api/product-sales", {
                    body: {
                        id: uuid(),
                        branchDayId: app.today.branchDayId,
                        sessionId: s?.id ?? null,
                        clientId: null,
                        isWalkIn: !s,
                        productId: item.productId,
                        quantity: qty,
                    },
                }),
            );
            invalidate(`inv:${app.branchId}`, `dash:${app.branchId}`, "moves:");
            open = false;
            onsaved();
        }, "Sale recorded");
    }
</script>

<Dialog bind:open title="Product sale" onsubmit={save} submitText="Record sale" busy={a.busy} error={a.error}>
    <div class="form-grid">
        <label class="field wide"
            ><span class="req">Product</span>
            <select bind:value={productId} required>
                <option value="" disabled>Select a product in stock</option>
                {#each inv as i (i.id)}<option value={i.productId}>{i.productName} · {peso(i.unitPrice)} · {i.currentStock} left</option>{/each}
            </select>
        </label>
        <label class="field"
            ><span class="req">Quantity</span><input class="num" type="number" min="1" max={item?.currentStock ?? 999} bind:value={qty} required /></label
        >
        <label class="field">Total<input class="num" value={peso(total)} disabled /></label>
        <label class="field wide"
            >Sold during session
            <select bind:value={linkSession}>
                <option value="">None: walk-in sale</option>
                {#each sessions as s (s.id)}<option value={s.id}>{s.clientName ?? "(details erased)"}</option>{/each}
            </select>
        </label>
    </div>
</Dialog>
