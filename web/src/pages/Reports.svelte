<script lang="ts">
    import { download, type S } from "../api/client";
    import { app, branchName } from "../lib/app.svelte";
    import { Query } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { go, route, viewSetting } from "../lib/router.svelte";
    import { peso, day, shortDay, sum, operationalDate, monthStart, addDays } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import RangePicker, { type Preset } from "../components/RangePicker.svelte";
    import State from "../components/State.svelte";

    const bid = $derived(app.branchId);
    const today = operationalDate();
    const from = $derived(route.current.query.get("from") ?? monthStart(today));
    const to = $derived(route.current.query.get("to") ?? today);
    const year = $derived(Number(from.slice(0, 4)));
    const month = $derived(Number(from.slice(5, 7)));
    const wholeMonth = $derived(from === monthStart(from) && to.slice(0, 7) === from.slice(0, 7));

    const daily = new Query<S["DailySalesSummaryBrowseResponse"]>();
    const monthly = new Query<S["MonthlyRemittanceSummaryResponse"]>();
    $effect(() => {
        const l = load.dailySummaries(bid, from, to);
        daily.load(l.key, l.fn);
    });
    $effect(() => {
        if (!wholeMonth) return;
        const l = load.monthly(bid, year, month);
        monthly.load(l.key, l.fn);
    });

    const rows = $derived([...(daily.data?.entries ?? [])].sort((a, b) => b.date.localeCompare(a.date)));
    const total = (k: keyof S["DailySalesSummaryResponse"]) => peso(sum(rows.map((r) => r[k] as string)));

    const lastMonthStart = monthStart(addDays(monthStart(today), -1));
    const presets: Preset[] = [
        { key: "today", label: "Today", range: () => ({ from: today, to: today }) },
        { key: "yesterday", label: "Yesterday", range: () => ({ from: addDays(today, -1), to: addDays(today, -1) }) },
        { key: "7d", label: "Last 7 days", range: () => ({ from: addDays(today, -6), to: today }) },
        { key: "30d", label: "Last 30 days", range: () => ({ from: addDays(today, -29), to: today }) },
        { key: "month", label: "This month", range: () => ({ from: monthStart(today), to: today }) },
        { key: "lastmonth", label: "Last month", range: () => ({ from: lastMonthStart, to: addDays(monthStart(today), -1) }) },
        { key: "year", label: "This year", range: () => ({ from: `${today.slice(0, 4)}-01-01`, to: today }) },
        {
            key: "lastyear",
            label: "Last year",
            range: () => ({ from: `${Number(today.slice(0, 4)) - 1}-01-01`, to: `${Number(today.slice(0, 4)) - 1}-12-31` }),
        },
    ];
    function setRange(f: string, t: string) {
        go("/reports", { ...Object.fromEntries(route.current.query), from: f, to: t });
    }
    function shiftMonth(n: number) {
        const d = new Date(Date.UTC(year, month - 1 + n, 1));
        const f = d.toISOString().slice(0, 10);
        const end = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).toISOString().slice(0, 10);
        setRange(f, end < today ? end : today);
    }
    const monthName = $derived(new Date(Date.UTC(year, month - 1, 1)).toLocaleString("en-PH", { month: "long", year: "numeric", timeZone: "UTC" }));

    // Who the downloaded report covers: this branch, or every branch of a kind you can see.
    const scopeVs = viewSetting("scope", "branch");
    let scope = $state(scopeVs.read);
    $effect(() => scopeVs.keep(scope));
    const scopes = [
        { v: "ALL", label: "All branches" },
        { v: "CLINIC", label: "All clinics" },
        { v: "PROVINCIAL_TOUR", label: "All provincial tours" },
        { v: "MEDICAL_MISSION", label: "All medical missions" },
    ];
    const rangeLabel = $derived(from === to ? day(from) : `${day(from)} to ${day(to)}`);
    let busy = $state(false);
    async function get(format: "xlsx" | "pdf") {
        busy = true;
        const path = scope === "branch" ? `/api/branches/${bid}/export/report` : "/api/branches/export/report";
        const query: Record<string, string> = { from, to, format };
        if (scope !== "branch") query.type = scope;
        await act(() => download(path, query), "Report downloaded");
        busy = false;
    }
</script>

<PageHead title="Sales reports" sub={branchName(bid)}>
    <span class="daynav monthnav">
        <button onclick={() => shiftMonth(-1)} title="Previous month">‹</button>
        <RangePicker mode="day" {from} {to} {presets} max={today} clearable={false} onchange={(r) => setRange(r.from || r.to, r.to || r.from)} />
        <button onclick={() => shiftMonth(1)} title="Next month" disabled={to >= today}>›</button>
    </span>
</PageHead>

{#if wholeMonth && monthly.data}
    <h2 class="sub">{monthName}: sent to the office</h2>
    {#if monthly.data.totalRemittances === 0}
        <p class="notice mb">No remittances sent for {monthName} yet. The table below shows what was recorded.</p>
    {:else}
        <div class="stats mb">
            <div>
                <div class="k">Remittances sent</div>
                <div class="v">{monthly.data.totalRemittances}</div>
            </div>
            <div>
                <div class="k">Income</div>
                <div class="v">{peso(monthly.data.grossIncome)}</div>
            </div>
            <div>
                <div class="k">Pay</div>
                <div class="v">{peso(monthly.data.totalCompensation)}</div>
            </div>
            <div>
                <div class="k">Expenses</div>
                <div class="v">{peso(monthly.data.totalExpenses)}</div>
            </div>
            <div>
                <div class="k">Net</div>
                <div class="v">{peso(monthly.data.netIncome)}</div>
            </div>
            <div>
                <div class="k">Sessions / product sales</div>
                <div class="v">{monthly.data.sessionCount} / {monthly.data.productCount}</div>
            </div>
        </div>
    {/if}
    <h2 class="sub">Recorded each day</h2>
{/if}

<State q={daily} empty={!!daily.data && rows.length === 0} emptyText="No sales in this range." />
{#if rows.length}
    <div class="table-wrap mb">
        <table class="grid">
            <thead>
                <tr>
                    <th>Day</th>
                    <th class="num">Income</th>
                    <th class="num hide-sm">Pay</th>
                    <th class="num">Expenses</th>
                    <th class="num">Net</th>
                    <th class="num hide-sm">Product sales</th>
                    <th class="num hide-sm">Commission</th>
                </tr>
            </thead>
            <tbody>
                {#each rows as r (r.branchDayId)}
                    <tr class="click" onclick={() => go("/finance", { date: r.date })}>
                        <td class="nowrap"><span class="hide-sm">{day(r.date)}</span><span class="show-sm">{shortDay(r.date)}</span></td>
                        <td class="num">{peso(r.grossIncome)}</td>
                        <td class="num hide-sm">{peso(r.totalCompensation)}</td>
                        <td class="num">{peso(r.totalExpenses)}</td>
                        <td class="num"><b>{peso(r.netIncome)}</b></td>
                        <td class="num hide-sm">{peso(r.totalProductSales)}</td>
                        <td class="num hide-sm">{peso(r.totalCommission)}</td>
                    </tr>
                {/each}
            </tbody>
            <tfoot>
                <tr>
                    <td>{rows.length} {rows.length === 1 ? "day" : "days"}</td>
                    <td class="num">{total("grossIncome")}</td>
                    <td class="num hide-sm">{total("totalCompensation")}</td>
                    <td class="num">{total("totalExpenses")}</td>
                    <td class="num">{total("netIncome")}</td>
                    <td class="num hide-sm">{total("totalProductSales")}</td>
                    <td class="num hide-sm">{total("totalCommission")}</td>
                </tr>
            </tfoot>
        </table>
    </div>
    {#if daily.data?.nextCursor}<div class="faint mb">Showing the latest 100 days. Narrow the range to see older days.</div>{/if}
{/if}

<section class="panel">
    <header><h2>Report for your accountant</h2></header>
    <div class="body">
        <p class="faint mb-s">
            {rangeLabel}. A summary, then every session, product sale, expense, payout and remittance behind it. Change the dates at the top to pick a day,
            month or year.
        </p>
        <div class="row wrap">
            <select bind:value={scope} aria-label="Branches in the report">
                <option value="branch">{branchName(bid)}</option>
                {#each scopes as o (o.v)}<option value={o.v}>{o.label}</option>{/each}
            </select>
            <button class="primary" disabled={busy} onclick={() => get("xlsx")}>Download Excel</button>
            <button disabled={busy} onclick={() => get("pdf")}>Download PDF</button>
        </div>
    </div>
</section>

<style>
    .mb {
        margin-bottom: 12px;
    }
    .mb-s {
        margin: 0 0 8px;
    }
    .sub {
        font-size: 13px;
        margin: 0 0 6px;
    }
</style>
