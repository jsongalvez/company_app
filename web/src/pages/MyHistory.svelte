<script lang="ts">
    import type { S } from "../api/client";
    import { Query } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { go, route } from "../lib/router.svelte";
    import { peso, day, shortDay, time, operationalDate, monthStart, addDays } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import RangePicker, { type Preset } from "../components/RangePicker.svelte";
    import State from "../components/State.svelte";

    // Your own clock-ins and the money you got (pay, allowances, product commission).
    const today = operationalDate();
    const from = $derived(route.current.query.get("from") ?? monthStart(today));
    const to = $derived(route.current.query.get("to") ?? today);

    const q = new Query<S["MyHistoryResponse"]>();
    $effect(() => {
        const l = load.myHistory(from, to);
        q.load(l.key, l.fn);
    });

    const lastMonthStart = monthStart(addDays(monthStart(today), -1));
    const presets: Preset[] = [
        { key: "7d", label: "Last 7 days", range: () => ({ from: addDays(today, -6), to: today }) },
        { key: "month", label: "This month", range: () => ({ from: monthStart(today), to: today }) },
        { key: "lastmonth", label: "Last month", range: () => ({ from: lastMonthStart, to: addDays(monthStart(today), -1) }) },
        { key: "90d", label: "Last 90 days", range: () => ({ from: addDays(today, -89), to: today }) },
        { key: "year", label: "This year", range: () => ({ from: `${today.slice(0, 4)}-01-01`, to: today }) },
    ];

    const KIND: Record<string, string> = { PAY: "Pay", ALLOWANCE: "Allowance", COMMISSION: "Product commission" };

    function hours(s: S["MyShift"]): string {
        if (!s.clockOut) return "Now";
        const m = Math.round((new Date(s.clockOut).getTime() - new Date(s.clockIn).getTime()) / 60000);
        return `${Math.floor(m / 60)}h ${String(m % 60).padStart(2, "0")}m`;
    }
    const shifts = $derived(q.data?.shifts ?? []);
    const earnings = $derived(q.data?.earnings ?? []);
</script>

<PageHead title="My shifts & pay" sub="Only you see this page">
    <RangePicker
        mode="day"
        {from}
        {to}
        {presets}
        max={today}
        clearable={false}
        onchange={(r) => go("/my-history", { from: r.from || r.to, to: r.to || r.from })}
    />
</PageHead>

<State {q} />
{#if q.data}
    <div class="stats mb">
        <div>
            <div class="k">Money received</div>
            <div class="v">{peso(q.data.totalEarned)}</div>
        </div>
        <div>
            <div class="k">Shifts</div>
            <div class="v">{shifts.length}</div>
        </div>
    </div>

    <h2 class="sub">Money received</h2>
    {#if earnings.length === 0}
        <p class="faint mb">Nothing paid to you in this range.</p>
    {:else}
        <div class="table-wrap mb">
            <table class="grid">
                <thead><tr><th>Date</th><th>For</th><th class="hide-sm">Branch</th><th class="num">Amount</th></tr></thead>
                <tbody>
                    {#each earnings as e, i (i)}
                        <tr>
                            <td
                                ><span class="hide-sm">{day(e.date)}</span><span class="show-sm"
                                    >{shortDay(e.date)}<br /><span class="faint">{e.branchName}</span></span
                                ></td
                            >
                            <td
                                >{KIND[e.kind ?? ""] ?? e.kind}{#if e.note}<div class="faint">{e.note}</div>{/if}</td
                            >
                            <td class="hide-sm">{e.branchName}</td>
                            <td class="num">{peso(e.amount)}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}

    <h2 class="sub">Clock-ins</h2>
    {#if shifts.length === 0}
        <p class="faint mb">No clock-ins in this range.</p>
    {:else}
        <div class="table-wrap mb">
            <table class="grid">
                <thead><tr><th>Date</th><th class="hide-sm">Branch</th><th>In</th><th>Out</th><th class="num">Hours</th></tr></thead>
                <tbody>
                    {#each shifts as s, i (i)}
                        <tr>
                            <td
                                ><span class="hide-sm">{day(s.date)}</span><span class="show-sm"
                                    >{shortDay(s.date)}<br /><span class="faint">{s.branchName}</span></span
                                ></td
                            >
                            <td class="hide-sm">{s.branchName}</td>
                            <td class="nowrap">{time(s.clockIn)}</td>
                            <td class="nowrap">{s.clockOut ? time(s.clockOut) : "Still in"}</td>
                            <td class="num nowrap">{hours(s)}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}
{/if}
