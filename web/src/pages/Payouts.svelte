<script lang="ts">
    import type { S } from "../api/client";
    import { app } from "../lib/app.svelte";
    import { fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { route, go } from "../lib/router.svelte";
    import { day, operationalDate, addDays } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import DayPay from "../components/DayPay.svelte";

    // Owed #25(a): the Owner pays at every branch from one page, wages and allowances only; commission stays with
    // each branch. The user: "A non-member Owner can pay members for earlier days too, not just today."
    const date = $derived(route.current.query.get("date") ?? operationalDate());
    const isToday = $derived(date === operationalDate());
    const branches = $derived([...app.accessible].sort((a, b) => a.name.localeCompare(b.name)));

    /** Each branch's day on the chosen date: undefined while loading, null when nothing was recorded there. */
    let days = $state<Record<string, S["RemittanceDayPickerEntryResponse"] | null>>({});
    $effect(() => {
        for (const b of branches) {
            const key = `${b.id}:${date}`;
            const l = load.branchDay(b.id, date);
            fetchCached(l.key, l.fn)
                .then((d) => (days[key] = d))
                .catch(() => (days[key] = null));
        }
    });

    function setDate(d: string) {
        go("/pay", { date: d === operationalDate() ? null : d });
    }
</script>

<PageHead title="Pay, all branches">
    <span class="daynav">
        <button onclick={() => setDate(addDays(date, -1))} aria-label="Previous day">◀</button>
        <input type="date" value={date} max={operationalDate()} onchange={(e) => setDate(e.currentTarget.value || operationalDate())} />
        <button onclick={() => setDate(addDays(date, 1))} disabled={isToday} aria-label="Next day">▶</button>
        <button onclick={() => setDate(operationalDate())} disabled={isToday}>Today</button>
    </span>
</PageHead>

<p class="mb"><b>{day(date)}</b></p>

{#each branches as b (b.id)}
    {@const d = days[`${b.id}:${date}`]}
    <section class="branch" aria-label={b.name}>
        <div class="row mb wrap">
            <h2>{b.name}</h2>
            {#if d?.status === "REMITTED"}<span class="mark info">Remitted</span>{:else if d && d.status !== "OPEN"}<span class="mark warn">Not remitted</span
                >{/if}
        </div>
        {#if d === undefined}
            <p class="muted">Loading…</p>
        {:else if d === null}
            <p class="muted">Nothing recorded.</p>
        {:else}
            <div class="cols"><DayPay branchId={b.id} dayId={d.id} status={d.status} /></div>
        {/if}
    </section>
{/each}

<style>
    .mb {
        margin-bottom: 10px;
    }
    .branch {
        margin-bottom: 20px;
    }
    .branch h2 {
        margin: 0;
        font-size: 16px;
    }
    .cols {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
        gap: 12px;
        align-items: start;
    }
    @media (max-width: 760px) {
        .cols {
            grid-template-columns: 1fr;
        }
    }
</style>
