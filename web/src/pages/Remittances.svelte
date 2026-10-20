<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { app, can, branchName } from "../lib/app.svelte";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { go, viewSetting } from "../lib/router.svelte";
    import { peso, label, day, dateTime, operationalDate, addDays } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";

    const bid = $derived(app.branchId);
    const q = new Query<S["RemittanceResponse"][]>();
    $effect(() => {
        const l = load.remittances(bid);
        q.load(l.key, l.fn);
    });
    const send = $derived(can("SUBMIT_REMITTANCE"));

    type F = "all" | "DRAFT" | "SUBMITTED";
    const showVs = viewSetting("show", "all", ["all", "DRAFT", "SUBMITTED"]);
    let filter = $state<F>(showVs.read as F);
    $effect(() => showVs.keep(filter));
    const rows = $derived((q.data ?? []).filter((r) => filter === "all" || r.status === filter).sort((a, b) => b.dateRangeEnd.localeCompare(a.dateRangeEnd)));
    const count = (f: F) => (q.data ?? []).filter((r) => f === "all" || r.status === f).length;

    const a = new Action();
    let open = $state(false);
    let type = $state<"SESSION" | "PRODUCT">("SESSION");
    let method = $state<"BANK_TRANSFER" | "HANDED_TO_ACCOUNTANT">("BANK_TRANSFER");
    let from = $state("");
    let to = $state("");
    function openNew() {
        a.reset();
        // Default: the last 7 closed days.
        to = addDays(operationalDate(), -1);
        from = addDays(to, -6);
        open = true;
    }
    async function create() {
        const id = uuid();
        const done = await a.run(() => ok(api.POST("/api/remittances", { body: { id, type, method, branchId: bid, dateRangeStart: from, dateRangeEnd: to } })));
        if (done) {
            open = false;
            invalidate(`rem:${bid}`);
            go(`/remittance/${id}`);
        }
    }
</script>

<PageHead title="Remittance" sub={branchName(bid)}>
    <div class="seg" role="group" aria-label="Filter">
        {#each [["all", "All"], ["DRAFT", "Drafts"], ["SUBMITTED", "Sent"]] as [k, t] (k)}
            <button class:on={filter === k} onclick={() => (filter = k as F)}>{t} <span class="faint">{count(k as F)}</span></button>
        {/each}
    </div>
    {#if send}<button class="primary" onclick={openNew}>New remittance</button>{/if}
</PageHead>

<State {q} empty={!!q.data && rows.length === 0} emptyText="No remittances yet." />
{#if rows.length}
    <div class="table-wrap">
        <table class="grid stack">
            <thead><tr><th>Days</th><th>Kind</th><th>How</th><th>Status</th><th>Sent</th><th class="num">Net</th></tr></thead>
            <tbody>
                {#each rows as r (r.id)}
                    <tr class="click" onclick={() => go(`/remittance/${r.id}`)}>
                        <td class="lead full"
                            >{day(r.dateRangeStart)}{#if r.dateRangeEnd !== r.dateRangeStart}&nbsp;– {day(r.dateRangeEnd)}{/if}</td
                        >
                        <td>{label(r.type)}</td>
                        <td class="muted hide-sm">{label(r.method)}</td>
                        <td><span class="mark {r.status === 'SUBMITTED' ? 'ok' : 'warn'}">{label(r.status)}</span></td>
                        <td class="muted hide-sm">{dateTime(r.submittedAt)}</td>
                        <td class="num">{peso(r.netIncome)}</td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
{/if}

<Dialog bind:open title="New remittance" width={420} onsubmit={create} submitText="Create draft" busy={a.busy} error={a.error}>
    <div class="muted">A draft can be changed freely. Its days lock only when you send it.</div>
    <div class="form-grid">
        <label class="field"
            >Kind
            <select bind:value={type}>
                <option value="SESSION">Sessions</option>
                <option value="PRODUCT">Product sales</option>
            </select>
        </label>
        <label class="field"
            >How
            <select bind:value={method}>
                <option value="BANK_TRANSFER">Bank transfer</option>
                <option value="HANDED_TO_ACCOUNTANT">Handed to accountant</option>
            </select>
        </label>
        <label class="field"><span class="req">From</span><input type="date" bind:value={from} max={to} required /></label>
        <label class="field"><span class="req">To</span><input type="date" bind:value={to} min={from} max={operationalDate()} required /></label>
    </div>
</Dialog>
