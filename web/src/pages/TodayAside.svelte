<script module lang="ts">
    import type { load } from "../lib/data";

    export type Roster = Awaited<ReturnType<ReturnType<typeof load.roster>["fn"]>>;
    // Opened folds stay open while the app is open; a fresh start folds them again.
    const unfolded = $state({ roster: false, booked: false });
</script>

<script lang="ts">
    import type { S } from "../api/client";
    import { app } from "../lib/app.svelte";
    import { today, markMember } from "../lib/today.svelte";
    import type { Query } from "../lib/cache.svelte";
    import { time } from "../lib/format";
    import State from "../components/State.svelte";
    import Bookings from "../components/Bookings.svelte";
    import { changed } from "../lib/changed";
    import type { Booking } from "../lib/bookings";

    let {
        bid,
        edit,
        member,
        phone,
        roster,
        booked,
        onstart,
        onchanged,
    }: {
        bid: string;
        edit: boolean;
        member: boolean;
        phone: boolean;
        roster: Query<Roster>;
        booked: Query<Booking[]>;
        onstart?: (b: Booking) => void;
        onchanged: () => void;
    } = $props();

    const rosterOpen = $derived(!phone || unfolded.roster);
    const bookedOpen = $derived(!phone || unfolded.booked);
    /** The roster with a just-confirmed mark shown before the server has answered. */
    const rosterShown = $derived(roster.data?.map((m) => (today.marking?.userId === m.userId ? { ...m, present: today.marking.present } : m)));

    async function setPresent(m: S["MemberAttendanceResponse"], present: boolean) {
        const mine = m.userId === app.me?.id;
        if (!confirm(`${present ? "Clock in" : "Clock out"} ${mine ? "yourself" : m.displayName}?`)) return;
        await markMember(m.userId, present, bid);
    }
</script>

<aside class="panel roster">
    {#if member}
        <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
        <header class:foldbar={phone} onclick={() => phone && (unfolded.roster = !unfolded.roster)}>
            <h2>
                {#if phone}<button class="fold" aria-expanded={rosterOpen}>Who's clocked in</button>{:else}Who's clocked in{/if}
            </h2>
            <span class="spacer"></span><span class="faint" use:changed={rosterShown?.filter((m) => m.present).length}
                >{rosterShown?.filter((m) => m.present).length ?? 0} of {rosterShown?.length ?? 0} in</span
            >
        </header>
        {#if rosterOpen}<State q={roster} empty={!!roster.data && roster.data.length === 0} emptyText="No members assigned." />{/if}
        {#if rosterOpen && roster.data?.length}
            <table class="grid">
                <tbody>
                    {#each rosterShown ?? [] as m, i (m.assignmentId)}
                        <tr>
                            <td class="faint num slot">{i + 1}</td>
                            <td class="ellipsis who" title={m.displayName}
                                >{m.displayName}{#if m.userId === app.me?.id}<span class="faint">&nbsp;(you)</span>{/if}</td
                            >
                            <td class="faint num nowrap">{m.present ? time(m.clockedInAt) : ""}</td>
                            <td class="right nowrap">
                                {#if !edit}
                                    {#if m.present}<span class="mark ok">Clocked in</span>{:else}<span class="faint">Not in</span>{/if}
                                {:else}
                                    <button
                                        class="sm clockbtn"
                                        class:ok={m.present}
                                        onclick={() => setPresent(m, !m.present)}
                                        aria-label="{m.present ? 'Clock out' : 'Clock in'} {m.displayName}">{m.present ? "Clock out" : "Clock in"}</button
                                    >
                                {/if}
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        {/if}
    {/if}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <header class:bk-head={member} class:foldbar={phone} onclick={() => phone && (unfolded.booked = !unfolded.booked)}>
        <h2>
            {#if phone}<button class="fold" aria-expanded={bookedOpen}>Booked visits</button>{:else}Booked visits{/if}
        </h2>
        <span class="spacer"></span><span class="faint">{booked.data?.length ?? 0}</span>
    </header>
    {#if bookedOpen}<State q={booked} empty={!!booked.data && booked.data.length === 0} emptyText="No visits booked." />{/if}
    {#if bookedOpen && booked.data?.length}
        <Bookings list={booked.data} {edit} {onstart} {onchanged} />
    {/if}
</aside>

<style>
    .roster {
        width: 300px;
        flex: none;
    }
    /* the name takes what the status and button leave, and is cut with "…" */
    .roster .clockbtn {
        width: 80px;
        justify-content: center;
    }
    .roster td.who {
        max-width: 0;
        width: 100%;
    }
    .roster table.grid td {
        border-bottom-color: var(--line);
    }
    .slot {
        width: var(--s5);
    }
    @media (max-width: 1000px) {
        .roster {
            width: auto;
        }
    }
    /* The whole title is the tap target; the arrow says whether it is open. */
    .foldbar {
        cursor: pointer;
    }
    .fold {
        all: unset;
        cursor: pointer;
        display: block;
        min-height: var(--s6);
        line-height: 32px;
    }
    .fold::before {
        content: "▸" / "";
        display: inline-block;
        width: 1.2em;
    }
    .fold[aria-expanded="true"]::before {
        content: "▾" / "";
    }
    .fold:focus-visible {
        outline: 2px solid var(--accent);
    }
    .bk-head {
        border-top: 1px solid var(--line);
    }
</style>
