<script lang="ts">
    import { untrack } from "svelte";
    import { type S } from "../api/client";
    import { Query, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { dateTime, label, changeText } from "../lib/format";
    import State from "./State.svelte";

    let { table, id, start = false }: { table: string; id: string; start?: boolean } = $props();
    let shown = $state(untrack(() => start));
    let opening = $state(false);
    const q = new Query<S["AuditLogEntryResponse"][]>();
    $effect(() => {
        if (!shown) return;
        const l = load.audit(table, id);
        q.load(l.key, l.fn);
    });
    // Fetch before opening, so the panel opens on its rows instead of a Loading line.
    async function toggle() {
        if (shown) return void (shown = false);
        const l = load.audit(table, id);
        opening = true;
        await fetchCached(l.key, l.fn).catch(() => undefined);
        opening = false;
        shown = true;
    }
</script>

<section class="panel">
    <header>
        <h2>Change history</h2>
        <span class="spacer"></span>
        <button class="sm" disabled={opening} onclick={toggle}>{shown ? "Hide" : "Show"}</button>
    </header>
    {#if shown}
        <State {q} empty={!!q.data && q.data.length === 0} emptyText="No changes recorded." />
        {#if q.data?.length}
            <div class="table-wrap">
                <table class="grid">
                    <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Change</th><th>Reason</th></tr></thead>
                    <tbody>
                        {#each q.data as e (e.id)}
                            <tr>
                                <td>{dateTime(e.changedAt)}</td>
                                <td>{e.changedByName ?? ""}</td>
                                <td
                                    >{label(e.action)}{#if e.isFlagged}<span class="mark warn">Needs review</span>{/if}</td
                                >
                                <td class="wrap mono">{changeText(e)}</td>
                                <td class="wrap">{e.reason ?? ""}</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        {/if}
    {/if}
</section>
