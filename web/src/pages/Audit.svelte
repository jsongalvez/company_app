<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { go, route } from "../lib/router.svelte";
    import { dateTime, label, changeText } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";

    type E = S["AuditLogEntryResponse"];
    const tables = new Query(load.auditTables().key, load.auditTables().fn);
    const flagged = new Query(load.auditFlagged().key, load.auditFlagged().fn);
    const tableLabel = $derived(Object.fromEntries((tables.data ?? []).map((t) => [t.tableName, t.label])));
    const open = $derived((flagged.data ?? []).filter((e) => !e.acknowledgedAt));

    function orderedRange(a: string, b: string) {
        return a && b && a > b ? { dateFrom: b, dateTo: a } : { dateFrom: a, dateTo: b };
    }
    // Filters live in the URL so a view can be shared or reloaded.
    const qp = $derived(route.current.query);
    const f = $derived({
        tableName: qp.get("table") ?? "",
        action: qp.get("action") ?? "",
        callerName: qp.get("who") ?? "",
        ...orderedRange(qp.get("from") ?? "", qp.get("to") ?? ""),
    });
    function setF(k: string, v: string) {
        const next: Record<string, string | null> = { table: f.tableName, action: f.action, who: f.callerName, from: f.dateFrom, to: f.dateTo };
        next[k] = v || null;
        go("/audit", next);
    }

    // A date range: if the two ends are picked backwards, swap them instead of failing.
    function setRange(from: string, to: string) {
        const r = orderedRange(from, to);
        go("/audit", { table: f.tableName || null, action: f.action || null, who: f.callerName || null, from: r.dateFrom || null, to: r.dateTo || null });
    }

    let entries = $state<E[]>([]);
    let cursor = $state<string | null>(null);
    let loading = $state(false);
    let error = $state<string | null>(null);
    async function fetchPage(reset: boolean) {
        loading = true;
        error = null;
        const query = {
            tableName: f.tableName || undefined,
            action: f.action || undefined,
            callerName: f.callerName || undefined,
            dateFrom: f.dateFrom || undefined,
            dateTo: f.dateTo || undefined,
            cursor: reset ? undefined : (cursor ?? undefined),
            limit: "100",
        };
        try {
            const r = await ok(api.GET("/api/audit-log/entries", { params: { query } }));
            entries = reset ? r.entries : [...entries, ...r.entries];
            cursor = r.nextCursor ?? null;
        } catch (e) {
            error = e instanceof Error ? e.message : String(e);
        } finally {
            loading = false;
        }
    }
    $effect(() => {
        void f;
        void fetchPage(true);
    });

    async function review(e: E) {
        if (await act(() => ok(api.PATCH("/api/audit-log/{entryId}/acknowledge", { params: { path: { entryId: e.id } } })), "Marked reviewed")) {
            invalidate("audit:flagged");
            flagged.refresh();
        }
    }

    const changes = (e: E) => (e.action === "UPDATE" ? changeText(e.oldValue, e.newValue) : "");
    let expanded = $state<string | null>(null);
</script>

<PageHead title="Change history" sub="Who changed what, and when. Nothing here can be edited." />

{#if open.length}
    <section class="panel mb">
        <header>
            <h2>Needs review</h2>
            <span class="spacer"></span><span class="faint">Changes made to closed or remitted days</span>
        </header>
        <table class="grid stack">
            <tbody>
                {#each open as e (e.id)}
                    <tr>
                        <td class="nowrap">{dateTime(e.changedAt)}</td>
                        <td class="lead">{e.changedByName ?? ""}</td>
                        <td>{label(e.action)} · {tableLabel[e.tableName] ?? e.tableName}</td>
                        <td class="muted">{e.branchName ?? ""}</td>
                        <td class="wrap full">{e.reason ? `“${e.reason}”` : ""}</td>
                        <td class="right"><button class="sm" onclick={() => review(e)}>Mark reviewed</button></td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </section>
{/if}

<div class="row wrap mb filters">
    <select value={f.tableName} onchange={(e) => setF("table", e.currentTarget.value)} aria-label="What">
        <option value="">Everything</option>
        {#each tables.data ?? [] as t (t.tableName)}<option value={t.tableName}>{t.label}</option>{/each}
    </select>
    <select value={f.action} onchange={(e) => setF("action", e.currentTarget.value)} aria-label="Change">
        <option value="">Any change</option>
        <option value="INSERT">Added</option>
        <option value="UPDATE">Changed</option>
        <option value="DELETE">Removed</option>
    </select>
    <input type="search" placeholder="Person" value={f.callerName} onchange={(e) => setF("who", e.currentTarget.value.trim())} aria-label="Person" />
    <span class="daynav range">
        <span class="muted">Between</span>
        <input type="date" value={f.dateFrom} max={f.dateTo || undefined} onchange={(e) => setRange(e.currentTarget.value, f.dateTo)} aria-label="From day" />
        <span class="muted">and</span>
        <input type="date" value={f.dateTo} min={f.dateFrom || undefined} onchange={(e) => setRange(f.dateFrom, e.currentTarget.value)} aria-label="To day" />
    </span>
    {#if Object.values(f).some(Boolean)}<button onclick={() => go("/audit")}>Clear</button>{/if}
</div>

{#if error}<div class="notice bad">{error}</div>{/if}
{#if !loading && !error && entries.length === 0}<div class="faint">No changes match.</div>{/if}
{#if entries.length}
    <div class="table-wrap">
        <table class="grid stack">
            <thead><tr><th>When</th><th>Who</th><th>Change</th><th>What</th><th>Branch</th><th>Details</th></tr></thead>
            <tbody>
                {#each entries as e (e.id)}
                    <tr class="click" onclick={() => (expanded = expanded === e.id ? null : e.id)}>
                        <td class="nowrap">{dateTime(e.changedAt)}</td>
                        <td class="lead">{e.changedByName ?? ""}</td>
                        <td
                            >{label(e.action)}{#if e.isFlagged}<span class="mark warn">{e.acknowledgedAt ? "Reviewed" : "Needs review"}</span>{/if}</td
                        >
                        <td>{tableLabel[e.tableName] ?? e.tableName}</td>
                        <td class="muted hide-sm">{e.branchName ?? ""}</td>
                        <td class="wrap muted full" class:ellipsis={expanded !== e.id}
                            >{changes(e)}{#if e.reason}<span>&nbsp;Reason: “{e.reason}”</span>{/if}</td
                        >
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
{/if}
{#if loading}<div class="faint pad">Loading…</div>{:else if cursor}<div class="pad"><button onclick={() => fetchPage(false)}>Show more</button></div>{/if}

<style>
    .mb {
        margin-bottom: 12px;
    }
    .pad {
        padding: 8px 0;
    }
    td.ellipsis {
        max-width: 420px;
        white-space: nowrap;
    }
</style>
