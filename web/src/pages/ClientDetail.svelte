<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { can, canAnywhere } from "../lib/app.svelte";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { clientName, label, peso, shortDay, sum } from "../lib/format";
    import { draftFrom, updateBody } from "../lib/client-draft";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";
    import History from "../components/History.svelte";
    import ClientForm, { type ClientDraft } from "../components/ClientForm.svelte";

    let { id }: { id: string } = $props();
    const q = new Query<S["ClientResponse"]>();
    $effect(() => {
        const l = load.client(id);
        q.load(l.key, l.fn);
    });
    const hq = new Query<S["ClientHistoryResponse"]>();
    $effect(() => {
        const l = load.clientHistory(id);
        hq.load(l.key, l.fn);
    });
    const c = $derived(q.data);
    const sessions = $derived(hq.data?.sessions ?? []);
    const sales = $derived(hq.data?.sales ?? []);
    const statusMark = (s: S["ClientSessionItem"]) => (s.voided ? "bad" : s.status === "COMPLETED" ? "ok" : s.status === "PENDING" ? "info" : "warn");
    const anonymized = $derived(!!c && !c.firstName && !c.lastName);
    const edit = $derived(can("EDIT_BRANCH_DATA") || canAnywhere("EDIT_BRANCH_DATA"));

    const a = new Action();
    let open = $state(false);
    let draft = $state<ClientDraft | null>(null);
    async function save() {
        if (!c || !draft) return;
        const body = updateBody(c, draft);
        if (Object.keys(body).length === 0) {
            open = false;
            return;
        }
        if (await a.run(() => ok(api.PATCH("/api/clients/{clientId}", { params: { path: { clientId: id } }, body })), "Client saved")) {
            invalidate(`client:${id}`, "clients:", `audit:client:${id}`);
            open = false;
            q.refresh();
        }
    }

    const an = new Action();
    let anonOpen = $state(false);
    let confirmText = $state("");
    async function anonymize() {
        if (await an.run(() => ok(api.POST("/api/clients/{clientId}/anonymize", { params: { path: { clientId: id } } })), "Client anonymized")) {
            invalidate(`client:${id}`, "clients:");
            anonOpen = false;
            q.refresh();
        }
    }
</script>

<PageHead title={c ? clientName(c) : "Client"}>
    <a class="btn" href="#/clients">All clients</a>
    {#if c && edit && !anonymized}
        <button class="danger" onclick={() => ((confirmText = ""), an.reset(), (anonOpen = true))}>Erase personal details</button>
        <button class="primary" onclick={() => ((draft = draftFrom(c)), a.reset(), (open = true))}>Edit</button>
    {/if}
</PageHead>

<State {q} />

{#if c}
    {#if anonymized}<div class="notice warn mb">This client's personal details were erased. Only gender and age remain, for reports.</div>{/if}
    <section class="panel mb">
        <div class="body">
            <dl class="kv">
                <dt>Gender / age</dt>
                <dd>{c.gender === "M" ? "Male" : c.gender === "F" ? "Female" : ""} · {c.age}</dd>
                <dt>Phone</dt>
                <dd>{c.phoneNumber ?? ""}</dd>
                <dt>Address</dt>
                <dd>{c.address ?? ""}</dd>
                <dt>Blood pressure</dt>
                <dd>{c.systolicBp && c.diastolicBp ? `${c.systolicBp}/${c.diastolicBp}` : ""}</dd>
                <dt>Conditions</dt>
                <dd class="pre">{c.medicalConditions ?? ""}</dd>
                <dt>Visits</dt>
                <dd>
                    {c.sessionCount}{#if c.earlierVisits}, plus {c.earlierVisits} before the app{/if}
                </dd>
            </dl>
        </div>
    </section>
    <section class="panel mb">
        <header>
            <h2>Sessions</h2>
            <span class="spacer"></span><span class="faint">{sessions.length}</span>
        </header>
        {#if hq.error}<div class="body"><State q={hq} /></div>
        {:else if hq.data && sessions.length === 0}<div class="body faint">No sessions yet.</div>
        {:else if sessions.length}
            <table class="grid stack">
                <thead><tr><th>Day</th><th>Status</th><th>Type</th><th>Practitioner</th><th>Illnesses</th><th>Branch</th><th class="num">Price</th></tr></thead>
                <tbody>
                    {#each sessions as s (s.id)}
                        <tr class="click" onclick={() => (location.hash = `#/sessions/${s.id}`)}>
                            <td class="nowrap lead">{shortDay(s.date)}</td>
                            <td><span class="mark {statusMark(s)}">{s.voided ? "Voided" : label(s.status)}</span></td>
                            <td class="muted">{label(s.type)}</td>
                            <td>{s.practitioners.join(", ")}</td>
                            <td class="wrap full muted"
                                >{s.concerns.join(", ")}{#if s.remarks}<div class="faint">{s.remarks}</div>{/if}</td
                            >
                            <td class="muted hide-sm">{s.branchName}</td>
                            <td class="num">{peso(s.price)}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        {/if}
    </section>
    <section class="panel mb">
        <header>
            <h2>Products bought</h2>
            <span class="spacer"></span>{#if sales.length}<span class="faint">{peso(sum(sales.map((x) => x.total)))}</span>{/if}
        </header>
        {#if hq.data && sales.length === 0}<div class="body faint">No products bought yet.</div>
        {:else if sales.length}
            <table class="grid stack">
                <thead><tr><th>Day</th><th>Product</th><th>Branch</th><th class="num">Total</th></tr></thead>
                <tbody>
                    {#each sales as x (x.id)}
                        <tr>
                            <td class="nowrap">{shortDay(x.date)}</td>
                            <td class="lead">{x.productName} <span class="muted">×{x.quantity}</span></td>

                            <td class="muted hide-sm">{x.branchName}</td>
                            <td class="num">{peso(x.total)}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        {/if}
    </section>
    <History table="client" {id} />
{/if}

<Dialog bind:open title="Edit client" width={620} onsubmit={save} busy={a.busy} error={a.error}>
    {#if draft}<ClientForm bind:v={draft} />{/if}
</Dialog>

<Dialog
    bind:open={anonOpen}
    title="Erase personal details"
    width={420}
    onsubmit={anonymize}
    submitText="Erase details"
    danger
    busy={an.busy || confirmText.trim().toUpperCase() !== "ERASE"}
    error={an.error}
>
    <div>This erases the name, phone, address and medical notes for good. Session records stay, with gender and age only. This can't be undone.</div>
    <label class="field">Type ERASE to confirm<input bind:value={confirmText} autocomplete="off" /></label>
</Dialog>

<style>
    .mb {
        margin-bottom: 10px;
    }
    .kv {
        display: grid;
        grid-template-columns: 120px 1fr;
        gap: 6px 10px;
        margin: 0;
    }
    .kv dt {
        color: var(--text-2);
    }
    .kv dd {
        margin: 0;
    }
    .pre {
        white-space: pre-wrap;
    }
</style>
