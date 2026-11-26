<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import type { Query } from "../lib/cache.svelte";
    import { act } from "../lib/act.svelte";
    import { sortConcerns } from "../lib/concerns";

    let {
        id,
        s,
        cq,
        edit,
        why,
        onchange,
    }: {
        id: string;
        s: S["DashboardSessionResponse"];
        /** The shared illness list; the session page refreshes it too. */
        cq: Query<S["ConcernResponse"][]>;
        edit: boolean;
        why: (what: string) => Promise<string | null>;
        onchange: () => void;
    } = $props();

    const sessionConcernIds = $derived(new Set(s.concerns?.map((c) => c.id) ?? []));

    // Concerns: type to find one on the shared list, or add a new one by name.
    let cText = $state("");
    const cTyped = $derived(cText.trim().replace(/\s+/g, " "));
    const cMatches = $derived.by(() => {
        const t = cTyped.toLowerCase();
        const free = (cq.data ?? []).filter((c) => !sessionConcernIds.has(c.id));
        const list = t ? free.filter((c) => c.label.toLowerCase().includes(t)) : free;
        return sortConcerns(list).slice(0, 12);
    });
    const cExact = $derived((cq.data ?? []).find((c) => c.label.toLowerCase() === cTyped.toLowerCase()) ?? null);
    async function addConcern(concernId: string) {
        const rs = await why("Add illness");
        if (rs === null) return;
        if (
            await act(() =>
                ok(api.POST("/api/sessions/{sessionId}/concerns", { params: { path: { sessionId: id } }, body: { concernId, reason: rs || null } })),
            )
        ) {
            cText = "";
            onchange();
        }
    }
    async function addNewConcern() {
        const name = cTyped;
        if (!name) return;
        const rs = await why("Add illness");
        if (rs === null) return;
        if (
            await act(
                () =>
                    ok(
                        api.POST("/api/sessions/{sessionId}/promote-concern", {
                            params: { path: { sessionId: id } },
                            body: { id: uuid(), label: name, reason: rs || null, clearOtherConcerns: false },
                        }),
                    ),
                "Illness added",
            )
        ) {
            cText = "";
            cq.refresh();
            onchange();
        }
    }
    function concernEnter(e: KeyboardEvent) {
        if (e.key !== "Enter") return;
        e.preventDefault();
        if (cExact && !sessionConcernIds.has(cExact.id)) addConcern(cExact.id);
        else if (!cExact && cTyped) addNewConcern();
    }
    async function removeConcern(c: S["ConcernResponse"]) {
        const rs = await why("Remove illness");
        if (rs === null) return;
        if (
            await act(() =>
                ok(
                    api.DELETE("/api/sessions/{sessionId}/concerns/{concernId}", {
                        params: { path: { sessionId: id, concernId: c.id } },
                        body: { reason: rs || null },
                    }),
                ),
            )
        )
            onchange();
    }
    let manageList = $state(false);
    async function removeFromList(c: S["ConcernResponse"]) {
        if (!confirm(`Take "${c.label}" off the illness list for everyone? Past sessions keep it.`)) return;
        if (await act(() => ok(api.DELETE("/api/concerns/{concernId}", { params: { path: { concernId: c.id } } })), "Taken off the list")) {
            cq.refresh();
        }
    }
</script>

<section class="panel">
    <header><h2>Illnesses</h2></header>
    {#if s.concerns?.length}
        <table class="grid ill">
            <tbody>
                {#each s.concerns as c (c.id)}
                    <tr>
                        <td>{c.label}</td>
                        {#if edit}<td class="right x"
                                ><button class="sm" onclick={() => removeConcern(c)} aria-label="Remove {c.label}" title="Remove from this session">×</button
                                ></td
                            >{/if}
                    </tr>
                {/each}
            </tbody>
        </table>
    {:else}<div class="body faint">None recorded.</div>{/if}
    {#if edit}
        <div class="body col concern-add">
            <input placeholder="Type an illness, e.g. back pain" aria-label="Add illness" bind:value={cText} onkeydown={concernEnter} />
            {#if cTyped || cMatches.length}
                <div class="table-wrap clist">
                    <table class="grid">
                        <tbody>
                            {#each cMatches as c (c.id)}
                                <tr class="click" onclick={() => addConcern(c.id)}><td>{c.label}</td><td class="right faint">Add</td></tr>
                            {/each}
                            {#if cTyped && !cExact}
                                <tr class="click" onclick={addNewConcern}><td>Add “{cTyped}” as a new illness</td><td class="right faint">New</td></tr>
                            {:else if cExact && sessionConcernIds.has(cExact.id)}
                                <tr><td class="faint" colspan="2">Already on this session.</td></tr>
                            {/if}
                        </tbody>
                    </table>
                </div>
            {/if}
            <div>
                <button class="sm" onclick={() => (manageList = !manageList)}>{manageList ? "Done" : "Fix the illness list"}</button>
            </div>
            {#if manageList}
                <div class="table-wrap clist">
                    <table class="grid">
                        <tbody>
                            {#each cq.data ?? [] as c (c.id)}
                                <tr
                                    ><td>{c.label}</td><td class="right"
                                        ><button class="sm" onclick={() => removeFromList(c)} aria-label="Take {c.label} off the list">Remove</button></td
                                    ></tr
                                >
                            {/each}
                        </tbody>
                    </table>
                </div>
            {/if}
        </div>
    {/if}
</section>

<style>
    /* Fixed columns: the × stays put however long the names are. */
    table.ill {
        table-layout: fixed;
    }
    table.ill td.x {
        width: 48px;
    }
    .concern-add {
        gap: var(--s1);
    }
    .clist {
        max-height: 220px;
    }
</style>
