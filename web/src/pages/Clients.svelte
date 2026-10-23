<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { can, canAnywhere } from "../lib/app.svelte";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load, warm } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { route, go } from "../lib/router.svelte";
    import { clientName } from "../lib/format";
    import { createBody, emptyDraft } from "../lib/client-draft";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";
    import ClientForm, { type ClientDraft } from "../components/ClientForm.svelte";

    const urlQ = $derived(route.current.query.get("q") ?? "");
    let q = $state(route.current.query.get("q") ?? "");
    $effect(() => {
        q = urlQ;
    });

    const res = new Query<S["ClientResponse"][]>("clients:", async () => []);
    $effect(() => {
        const term = urlQ.trim();
        if (term.length >= 2) {
            const l = load.clients(term);
            res.load(l.key, l.fn);
        } else res.data = undefined;
    });

    let timer: ReturnType<typeof setTimeout> | undefined;
    function typed() {
        clearTimeout(timer);
        timer = setTimeout(() => go("/clients", { q: q.trim() }), 200);
    }

    const a = new Action();
    let open = $state(false);
    let draft = $state<ClientDraft>(emptyDraft());
    const canCreate = $derived(can("EDIT_BRANCH_DATA") || canAnywhere("EDIT_BRANCH_DATA"));

    async function create() {
        let made: S["ClientResponse"] | undefined;
        if (await a.run(async () => (made = await ok(api.POST("/api/clients", { body: createBody(uuid(), draft) }))), "Client created")) {
            invalidate("clients:");
            open = false;
            if (made) go(`/clients/${made.id}`);
        }
    }
</script>

<PageHead title="Clients">
    {#if canCreate}<button class="primary" onclick={() => ((draft = emptyDraft(q.trim())), a.reset(), (open = true))}>New client</button>{/if}
</PageHead>

<div class="row mb">
    <input class="grow search" type="search" placeholder="Name or phone number" bind:value={q} oninput={typed} />
</div>

{#if urlQ.trim().length < 2}
    <div class="faint">Type 2+ letters.</div>
{:else}
    <State q={res} empty={!!res.data && res.data.length === 0} emptyText="No clients match." />
    {#if res.data?.length}
        <div class="table-wrap">
            <table class="grid">
                <thead
                    ><tr><th>Name</th><th>Gender</th><th class="num">Age</th><th>Phone</th><th>Address</th><th>Conditions</th><th class="num">Visits</th></tr
                    ></thead
                >
                <tbody>
                    {#each res.data as c (c.id)}
                        <tr class="click" onclick={() => go(`/clients/${c.id}`)} onmouseenter={() => warm(load.client(c.id))}>
                            <td><a href="#/clients/{c.id}">{clientName(c)}</a></td>
                            <td>{c.gender === "UNKNOWN" ? "" : c.gender}</td>
                            <td class="num">{c.age}</td>
                            <td>{c.phoneNumber ?? ""}</td>
                            <td class="ellipsis addr muted">{c.address ?? ""}</td>
                            <td class="ellipsis addr muted">{c.medicalConditions ?? ""}</td>
                            <td class="num">{c.sessionCount}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}
{/if}

<Dialog bind:open title="New client" width={620} onsubmit={create} submitText="Create client" busy={a.busy} error={a.error}>
    <ClientForm bind:v={draft} />
</Dialog>

<style>
    .mb {
        margin-bottom: 10px;
    }
    .search {
        max-width: 480px;
    }
    .addr {
        max-width: 220px;
    }
</style>
