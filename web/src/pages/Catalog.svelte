<script lang="ts">
    import { viewSetting } from "../lib/router.svelte";
    import { api, ok, uuid, type S } from "../api/client";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action, act } from "../lib/act.svelte";
    import { peso } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";
    import History from "../components/History.svelte";

    type P = S["ProductResponse"];
    const products = new Query(load.products().key, load.products().fn);
    const cats = new Query(load.categories().key, load.categories().fn);
    const catName = $derived(Object.fromEntries((cats.data ?? []).map((c) => [c.id, c.name])));

    const nameVs = viewSetting("q", "");
    const catVs = viewSetting("cat", "");
    const offVs = viewSetting("stopped", "");
    let filter = $state(nameVs.read);
    let cat = $state(catVs.read);
    let showOff = $state(offVs.read === "1");
    $effect(() => nameVs.keep(filter));
    $effect(() => catVs.keep(cat));
    $effect(() => offVs.keep(showOff ? "1" : ""));
    const rows = $derived(
        (products.data ?? [])
            .filter((p) => showOff || p.isActive)
            .filter((p) => !cat || p.productCategoryId === cat)
            .filter((p) => !filter || p.name.toLowerCase().includes(filter.toLowerCase()))
            .sort((a, b) => (catName[a.productCategoryId] ?? "").localeCompare(catName[b.productCategoryId] ?? "") || a.name.localeCompare(b.name)),
    );
    const offCount = $derived((products.data ?? []).filter((p) => !p.isActive).length);

    function refresh() {
        invalidate("products", "categories", "inv:");
        products.refresh();
        cats.refresh();
    }

    // One tap opens a product's change history under its row; fetched first, so it opens on its rows.
    let historyOf = $state<string | null>(null);
    async function openHistory(id: string) {
        if (historyOf === id) return void (historyOf = null);
        const l = load.audit("product", id);
        await fetchCached(l.key, l.fn).catch(() => undefined);
        historyOf = id;
    }

    // Add / edit product
    const pa = new Action();
    let pOpen = $state(false);
    let editing = $state<P | null>(null);
    let name = $state("");
    let categoryId = $state("");
    let price = $state("");
    let commission = $state("");
    function openProduct(p: P | null) {
        pa.reset();
        editing = p;
        name = p?.name ?? "";
        categoryId = p?.productCategoryId ?? cat;
        price = p?.unitPrice ?? "";
        commission = p?.commissionAmount ?? "0.00";
        pOpen = true;
    }
    async function saveProduct() {
        const e = editing;
        const fields = { name: name.trim(), productCategoryId: categoryId, unitPrice: price.trim(), commissionAmount: commission.trim() };
        const done = await pa.run(
            () =>
                e
                    ? ok(api.PATCH("/api/products/{productId}", { params: { path: { productId: e.id } }, body: fields }))
                    : ok(api.POST("/api/products", { body: { id: uuid(), ...fields } })),
            e ? "Product saved" : "Product added",
        );
        if (done) {
            pOpen = false;
            refresh();
        }
    }
    async function setActive(p: P, on: boolean) {
        if (!on && !confirm(`Stop selling ${p.name}? It disappears from sale lists. Past sales stay.`)) return;
        if (
            await act(
                () => ok(api.PATCH("/api/products/{productId}", { params: { path: { productId: p.id } }, body: { isActive: on } })),
                on ? "Selling again" : "Stopped selling",
            )
        )
            refresh();
    }

    // Add category
    const ca = new Action();
    let cOpen = $state(false);
    let cName = $state("");
    async function addCategory() {
        const id = uuid();
        if (await ca.run(() => ok(api.POST("/api/product-categories", { body: { id, name: cName.trim() } })), "Category added")) {
            cOpen = false;
            refresh();
            if (pOpen) categoryId = id;
        }
    }
    const money = "\\d+(\\.\\d{1,2})?";
</script>

<PageHead title="Products">
    <input type="search" placeholder="Filter by name" bind:value={filter} />
    <select bind:value={cat} aria-label="Category">
        <option value="">All categories</option>
        {#each cats.data ?? [] as c (c.id)}<option value={c.id}>{c.name}</option>{/each}
    </select>
    {#if offCount}<label class="check"><input type="checkbox" bind:checked={showOff} />Show stopped ({offCount})</label>{/if}
    <a href="#/audit?table=product_category">Category changes</a>
    <button onclick={() => (ca.reset(), (cName = ""), (cOpen = true))}>Add category</button>
    <button class="primary" onclick={() => openProduct(null)}>Add product</button>
</PageHead>

<State
    q={products}
    empty={!!products.data && rows.length === 0}
    emptyText={products.data?.length ? "No products match." : "No products yet. Add one to start selling."}
/>
{#if rows.length}
    <div class="table-wrap">
        <table class="grid stack">
            <thead><tr><th>Product</th><th>Category</th><th class="num">Price</th><th class="num">Commission</th><th></th></tr></thead>
            <tbody>
                {#each rows as p (p.id)}
                    <tr class:dim={!p.isActive}>
                        <td class="lead"
                            >{p.name}{#if !p.isActive}<span class="mark plain">Stopped</span>{/if}</td
                        >
                        <td class="muted">{catName[p.productCategoryId] ?? ""}</td>
                        <td class="num" data-label="Price">{peso(p.unitPrice)}</td>
                        <td class="num muted" data-label="Commission">{peso(p.commissionAmount)}</td>
                        <td class="right nowrap full">
                            <button class="sm" class:primary={historyOf === p.id} onclick={() => openHistory(p.id)}>History</button>
                            <button class="sm" onclick={() => openProduct(p)}>Edit</button>
                            {#if p.isActive}<button class="sm" onclick={() => setActive(p, false)}>Stop selling</button>{:else}<button
                                    class="sm"
                                    onclick={() => setActive(p, true)}>Sell again</button
                                >{/if}
                        </td>
                    </tr>
                    {#if historyOf === p.id}
                        <tr><td colspan="5"><History table="product" id={p.id} start /></td></tr>
                    {/if}
                {/each}
            </tbody>
        </table>
    </div>
{/if}

<Dialog bind:open={pOpen} title={editing ? `Edit ${editing.name}` : "Add product"} width={420} onsubmit={saveProduct} busy={pa.busy} error={pa.error}>
    <div class="form-grid">
        <label class="field wide"><span class="req">Name</span><input bind:value={name} required /></label>
        <label class="field wide"
            ><span class="req">Category</span>
            <span class="row">
                <select class="grow" bind:value={categoryId} required>
                    <option value="" disabled>Select</option>
                    {#each cats.data ?? [] as c (c.id)}<option value={c.id}>{c.name}</option>{/each}
                </select>
                <button type="button" onclick={() => (ca.reset(), (cName = ""), (cOpen = true))}>New</button>
            </span>
        </label>
        <label class="field"><span class="req">Price</span><input class="num" inputmode="decimal" bind:value={price} required pattern={money} /></label>
        <label class="field"
            ><span class="req">Commission per unit</span><input class="num" inputmode="decimal" bind:value={commission} required pattern={money} /></label
        >
    </div>
</Dialog>

<Dialog bind:open={cOpen} title="Add category" width={340} onsubmit={addCategory} submitText="Add" busy={ca.busy || !cName.trim()} error={ca.error}>
    <label class="field"><span class="req">Name</span><input bind:value={cName} required /></label>
</Dialog>
