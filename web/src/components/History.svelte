<script lang="ts">
    import { api, ok } from "../api/client";
    import { Query } from "../lib/cache.svelte";
    import { dateTime, label } from "../lib/format";
    import State from "./State.svelte";

    let { table, id }: { table: string; id: string } = $props();
    let shown = $state(false);
    const key = $derived(`audit:${table}:${id}`);
    const q = new Query<Awaited<ReturnType<typeof get>>>("audit:none", async () => []);
    function get(tableName: string, recordId: string) {
        return ok(api.GET("/api/audit-log", { params: { query: { tableName, recordId } } }));
    }
    $effect(() => {
        if (shown) q.load(key, () => get(table, id));
    });

    function diff(oldV: string | null | undefined, newV: string | null | undefined): string {
        try {
            const o = oldV ? JSON.parse(oldV) : {};
            const n = newV ? JSON.parse(newV) : {};
            const keys = [...new Set([...Object.keys(o), ...Object.keys(n)])].filter((k) => JSON.stringify(o[k]) !== JSON.stringify(n[k]));
            return keys.map((k) => `${k}: ${o[k] ?? "∅"} → ${n[k] ?? "∅"}`).join("; ");
        } catch {
            return newV ?? "";
        }
    }
</script>

<section class="panel">
    <header>
        <h2>History</h2>
        <span class="spacer"></span>
        <button class="sm" onclick={() => (shown = !shown)}>{shown ? "Hide" : "Show"}</button>
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
                                    >{label(e.action)}{#if e.isFlagged}<span class="mark warn">Flagged</span>{/if}</td
                                >
                                <td class="wrap mono">{e.action === "UPDATE" ? diff(e.oldValue, e.newValue) : ""}</td>
                                <td class="wrap">{e.reason ?? ""}</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        {/if}
    {/if}
</section>
