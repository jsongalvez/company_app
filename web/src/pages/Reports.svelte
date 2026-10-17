<script lang="ts">
    import { download, type S } from "../api/client";
    import { app, branchName } from "../lib/app.svelte";
    import { Query } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { go, route } from "../lib/router.svelte";
    import { peso, day, sum, operationalDate, monthStart } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
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

    function setRange(f: string, t: string) {
        go("/reports", { from: f, to: t });
    }
    function shiftMonth(n: number) {
        const d = new Date(Date.UTC(year, month - 1 + n, 1));
        const f = d.toISOString().slice(0, 10);
        const end = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).toISOString().slice(0, 10);
        setRange(f, end < today ? end : today);
    }
    const monthName = $derived(new Date(Date.UTC(year, month - 1, 1)).toLocaleString("en-PH", { month: "long", year: "numeric", timeZone: "UTC" }));

    let format = $state<"csv" | "pdf">("csv");
    let busy = $state(false);
    async function get(path: string, query: Record<string, string>, label: string) {
        busy = true;
        await act(() => download(path, { ...query, format }), `${label} downloaded`);
        busy = false;
    }
    const bp = (p: string) => p.replace("{branchId}", bid);
</script>

<PageHead title="Sales reports" sub={branchName(bid)}>
    <button onclick={() => shiftMonth(-1)} title="Previous month">‹</button>
    <button onclick={() => setRange(monthStart(today), today)}>This month</button>
    <button onclick={() => shiftMonth(1)} title="Next month" disabled={to >= today}>›</button>
    <input type="date" value={from} max={to} onchange={(e) => setRange(e.currentTarget.value || from, to)} aria-label="From" />
    <input type="date" value={to} min={from} max={today} onchange={(e) => setRange(from, e.currentTarget.value || to)} aria-label="To" />
</PageHead>

{#if wholeMonth && monthly.data}
    <div class="stats mb">
        <div>
            <div class="k">{monthName}: income</div>
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
            <div class="k">Remittances sent</div>
            <div class="v">{monthly.data.totalRemittances}</div>
        </div>
        <div>
            <div class="k">Sessions / product</div>
            <div class="v">{monthly.data.sessionCount} / {monthly.data.productCount}</div>
        </div>
    </div>
{/if}

<State q={daily} empty={!!daily.data && rows.length === 0} emptyText="No sales in this range." />
{#if rows.length}
    <div class="table-wrap mb">
        <table class="grid">
            <thead>
                <tr>
                    <th>Day</th>
                    <th class="num">Income</th>
                    <th class="num">Pay</th>
                    <th class="num">Expenses</th>
                    <th class="num">Net</th>
                    <th class="num hide-sm">Product sales</th>
                    <th class="num hide-sm">Commission</th>
                </tr>
            </thead>
            <tbody>
                {#each rows as r (r.branchDayId)}
                    <tr class="click" onclick={() => go("/finance", { date: r.date })}>
                        <td class="nowrap">{day(r.date)}</td>
                        <td class="num">{peso(r.grossIncome)}</td>
                        <td class="num">{peso(r.totalCompensation)}</td>
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
                    <td class="num">{total("totalCompensation")}</td>
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
    <header>
        <h2>Download</h2>
        <span class="spacer"></span>
        <div class="seg" role="group" aria-label="File type">
            <button class:on={format === "csv"} onclick={() => (format = "csv")}>Spreadsheet (CSV)</button>
            <button class:on={format === "pdf"} onclick={() => (format = "pdf")}>PDF</button>
        </div>
    </header>
    <div class="body row wrap">
        <button disabled={busy} onclick={() => get(bp("/api/branches/{branchId}/export/range"), { from, to }, "Report")}>This range</button>
        <button disabled={busy} onclick={() => get(bp("/api/branches/{branchId}/export/monthly"), { year: String(year), month: String(month) }, "Month")}
            >{monthName}</button
        >
        <button disabled={busy} onclick={() => get(bp("/api/branches/{branchId}/export/daily"), { date: to }, "Day")}>Day: {day(to)}</button>
        <button disabled={busy} onclick={() => get(bp("/api/branches/{branchId}/export/all-time"), {}, "All time")}>All time</button>
        <span class="spacer"></span>
        <button disabled={busy} onclick={() => get("/api/branches/export/provincial", { year: String(year), month: String(month) }, "Provincial tours")}
            >All provincial tours, {monthName}</button
        >
        <button disabled={busy} onclick={() => get("/api/branches/export/medical-mission", { year: String(year), month: String(month) }, "Medical missions")}
            >All medical missions, {monthName}</button
        >
    </div>
</section>

<style>
    .mb {
        margin-bottom: 12px;
    }
</style>
