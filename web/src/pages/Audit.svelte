<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { go, route } from "../lib/router.svelte";
    import { dateTime, label, changeText, manilaTime } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import RangePicker, { type Preset, type Range } from "../components/RangePicker.svelte";
    import State from "../components/State.svelte";

    type E = S["AuditLogEntryResponse"];
    const tables = new Query(load.auditTables().key, load.auditTables().fn);
    const flagged = new Query(load.auditFlagged().key, load.auditFlagged().fn);
    const tableLabel = $derived(Object.fromEntries((tables.data ?? []).map((t) => [t.tableName, t.label])));
    const open = $derived((flagged.data ?? []).filter((e) => !e.acknowledgedAt));

    // Relative spans stay relative: "last 1 hour" means the hour before each load.
    const SPANS: Record<string, number> = {
        "15m": 15 * 60,
        "1h": 3600,
        "3h": 3 * 3600,
        "12h": 12 * 3600,
        "1d": 86400,
        "3d": 3 * 86400,
        "1w": 7 * 86400,
        "30d": 30 * 86400,
    };
    const presets: Preset[] = [
        { key: "15m", label: "Last 15 min" },
        { key: "1h", label: "Last hour" },
        { key: "3h", label: "Last 3 hours" },
        { key: "12h", label: "Last 12 hours" },
        { key: "1d", label: "Last 24 hours" },
        { key: "3d", label: "Last 3 days" },
        { key: "1w", label: "Last 7 days" },
        { key: "30d", label: "Last 30 days" },
    ];

    // Filters live in the URL so a view can be shared or reloaded.
    const qp = $derived(route.current.query);
    const f = $derived({
        tableName: qp.get("table") ?? "",
        action: qp.get("action") ?? "",
        callerName: qp.get("who") ?? "",
        last: SPANS[qp.get("last") ?? ""] ? qp.get("last")! : "",
        from: qp.get("from") ?? "",
        to: qp.get("to") ?? "",
    });
    const params = () => ({
        table: f.tableName || null,
        action: f.action || null,
        who: f.callerName || null,
        from: f.from || null,
        to: f.to || null,
        last: f.last || null,
    });
    function setF(k: string, v: string) {
        go("/audit", { ...params(), [k]: v || null });
    }
    function setRange(r: Range) {
        go("/audit", { ...params(), from: r.from || null, to: r.to || null, last: r.last || null });
    }
    function bounds() {
        if (f.last) return { dateFrom: manilaTime(new Date(Date.now() - SPANS[f.last]! * 1000)), dateTo: undefined };
        const [a, b] = f.from && f.to && f.from > f.to ? [f.to, f.from] : [f.from, f.to];
        return { dateFrom: a || undefined, dateTo: b || undefined };
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
            ...bounds(),
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

    const changes = (e: E) => changeText(e);
    // Phone footer: "Changed session · Ana · Oct 19, 2:23 PM · Silang".
    const meta = (e: E) =>
        [
            e.subject ? `${label(e.action)} ${(tableLabel[e.tableName] ?? e.tableName).toLowerCase()}` : label(e.action),
            e.changedByName,
            dateTime(e.changedAt),
            e.branchName,
        ]
            .filter(Boolean)
            .join(" · ");
    let expanded = $state<string | null>(null);
</script>

<PageHead title="Change history" />

{#if open.length}
    <section class="panel mb">
        <header>
            <h2>Needs review</h2>
            <span class="spacer"></span><span class="faint">Closed or remitted days</span>
        </header>
        <table class="grid stack">
            <tbody>
                {#each open as e (e.id)}
                    <tr>
                        <td class="show-sm full"
                            ><div class="card">
                                <div class="subj">{e.subject ?? tableLabel[e.tableName] ?? e.tableName}</div>
                                {#if e.reason}<div class="muted">“{e.reason}”</div>{/if}
                                <div class="faint meta">{meta(e)}</div>
                            </div></td
                        >
                        <td class="nowrap hide-sm">{dateTime(e.changedAt)}</td>
                        <td class="hide-sm">{e.changedByName ?? ""}</td>
                        <td class="hide-sm"
                            >{label(e.action)} · {tableLabel[e.tableName] ?? e.tableName}{#if e.subject}<div class="subj">{e.subject}</div>{/if}</td
                        >
                        <td class="muted hide-sm">{e.branchName ?? ""}</td>
                        <td class="wrap hide-sm">{e.reason ? `“${e.reason}”` : ""}</td>
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
    <RangePicker from={f.from} to={f.to} last={f.last} {presets} max={manilaTime()} onchange={setRange} />
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
                        <td class="show-sm full"
                            ><div class="card">
                                <div class="subj">{e.subject ?? tableLabel[e.tableName] ?? e.tableName}</div>
                                {#if changes(e)}<div class="muted">{changes(e)}</div>{/if}
                                {#if e.reason}<div class="muted">Reason: “{e.reason}”</div>{/if}
                                <div class="faint meta">
                                    {meta(e)}{#if e.isFlagged}<span class="mark warn">{e.acknowledgedAt ? "Reviewed" : "Needs review"}</span>{/if}
                                </div>
                            </div></td
                        >
                        <td class="nowrap hide-sm">{dateTime(e.changedAt)}</td>
                        <td class="hide-sm">{e.changedByName ?? ""}</td>
                        <td class="hide-sm"
                            >{label(e.action)}{#if e.isFlagged}<span class="mark warn">{e.acknowledgedAt ? "Reviewed" : "Needs review"}</span>{/if}</td
                        >
                        <td class="hide-sm"
                            >{tableLabel[e.tableName] ?? e.tableName}{#if e.subject}<div class="subj">{e.subject}</div>{/if}</td
                        >
                        <td class="muted hide-sm">{e.branchName ?? ""}</td>
                        <td class="wrap muted hide-sm" class:ellipsis={expanded !== e.id}
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
    .subj {
        font-weight: 600;
    }
    .card {
        display: grid;
        gap: 2px;
        min-width: 0;
    }
    .card .muted {
        overflow-wrap: anywhere;
    }
    .meta {
        font-size: 0.9em;
    }
    td.ellipsis {
        max-width: 420px;
        white-space: nowrap;
    }
</style>
