<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { app, canBranchWide } from "../lib/app.svelte";
    import { askReason } from "../lib/ask.svelte";
    import { Query, fetchCached, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { peso, dateTime, sum } from "../lib/format";
    import History from "./History.svelte";
    import ProductSale from "./ProductSale.svelte";

    let {
        dayId,
        sessionId = null,
        edit,
        needReason,
        showDeleted = false,
        onchanged = () => {},
    }: {
        dayId: string;
        /** Only this session's sales; null lists the whole day. */
        sessionId?: string | null;
        edit: boolean;
        /** An earlier day: every change asks for its reason. */
        needReason: boolean;
        showDeleted?: boolean;
        onchanged?: () => void;
    } = $props();

    const q = new Query<S["ProductSaleResponse"][]>();
    $effect(() => {
        const l = load.productSales(dayId);
        q.load(l.key, l.fn);
    });
    const live = $derived((q.data ?? []).filter((x) => (!sessionId || x.sessionId === sessionId) && !x.deletedAt));
    const sales = $derived((q.data ?? []).filter((x) => (!sessionId || x.sessionId === sessionId) && (showDeleted || !x.deletedAt)));

    // The change dialog lists the branch's stock: fetch it now, so the dialog opens with its product filled.
    // Stock is a branch-wide read, which relief's one-day grant doesn't give.
    $effect(() => {
        if (!edit || !sales.length || !canBranchWide("VIEW_BRANCH_DATA")) return;
        const l = load.inventory(app.branchId);
        fetchCached(l.key, l.fn).catch(() => undefined);
    });

    let historyOf = $state<string | null>(null);
    // Fetch before opening, so the row opens on its history instead of a Loading line.
    async function openHistory(id: string) {
        if (historyOf === id) return void (historyOf = null);
        const l = load.audit("product_sale", id);
        await fetchCached(l.key, l.fn).catch(() => undefined);
        historyOf = id;
    }

    let changing = $state<S["ProductSaleResponse"] | null>(null);
    let changeOpen = $state(false);
    function openChange(x: S["ProductSaleResponse"]) {
        changing = x;
        changeOpen = true;
    }

    /** For a page that adds a sale itself (the session's Product sale button). */
    export function reload() {
        q.refresh();
    }

    function changed() {
        invalidate(`sales:${dayId}`, `inv:${app.branchId}`, `dash:${app.branchId}`, "dsess:", "dsum:", "moves:", "audit:product_sale:");
        q.refresh();
        onchanged();
    }

    async function remove(x: S["ProductSaleResponse"]) {
        const why = await askReason("Delete sale");
        if (!why) return;
        const done = await act(
            () => ok(api.DELETE("/api/product-sales/{saleId}", { params: { path: { saleId: x.id } }, body: { reason: why, expectedVersion: x.version } })),
            "Sale deleted",
        );
        if (done) changed();
    }

    async function restore(x: S["ProductSaleResponse"]) {
        const why = needReason ? await askReason("Restore sale") : "";
        if (why === null) return;
        const done = await act(
            () => ok(api.POST("/api/product-sales/{saleId}/restore", { params: { path: { saleId: x.id } }, body: { reason: why || null } })),
            "Sale restored",
        );
        if (done) changed();
    }
</script>

<!-- A session shows the panel only once it has sales; a day always shows it. -->
{#if sales.length || (!sessionId && q.data)}
    <section class="panel">
        <header>
            <h2>Product sales</h2>
            <span class="faint">{peso(sum(live.map((x) => x.totalAmountAtTime)))}</span>
        </header>
        {#if sales.length}
            <table class="grid">
                <thead><tr><th>Product</th><th class="num">Qty</th><th class="num">Total</th><th></th></tr></thead>
                <tbody>
                    {#each sales as x (x.id)}
                        <tr class:struck={!!x.deletedAt} title={x.deletedAt ? `Deleted ${dateTime(x.deletedAt)}: ${x.deletedReason ?? ""}` : ""}>
                            <td class="ellipsis">{x.productName}</td>
                            <td class="num">{x.quantity}</td>
                            <td class="num">{peso(x.totalAmountAtTime)}</td>
                            <td class="right nowrap">
                                <button class="sm" class:primary={historyOf === x.id} onclick={() => openHistory(x.id)} aria-label="Sale history"
                                    >History</button
                                >
                                {#if edit}
                                    {#if x.deletedAt}
                                        <button class="sm" onclick={() => restore(x)} aria-label="Restore sale">Restore</button>
                                    {:else}
                                        <button class="sm" onclick={() => openChange(x)} aria-label="Change sale">Change</button>
                                        <button class="sm" onclick={() => remove(x)} aria-label="Delete sale">×</button>
                                    {/if}
                                {/if}
                            </td>
                        </tr>
                        {#if historyOf === x.id}
                            <tr><td colspan="4"><History table="product_sale" id={x.id} start /></td></tr>
                        {/if}
                    {/each}
                </tbody>
            </table>
        {:else}
            <div class="faint pad">No product sales.</div>
        {/if}
    </section>
{/if}

<ProductSale bind:open={changeOpen} sale={changing} {needReason} onsaved={changed} />
