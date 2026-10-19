<script lang="ts">
    import type { S } from "../api/client";
    import { act } from "../lib/act.svelte";
    import { visitWhen, whoText, setBooking, type Booking } from "../lib/bookings";
    import { operationalDate } from "../lib/format";
    import BookVisit from "./BookVisit.svelte";
    import History from "./History.svelte";

    /** A list of booked visits with Change / Cancel, and Start for today's when allowed. */
    let {
        list,
        edit,
        showClient = true,
        showBranch = false,
        onstart,
        onchanged,
    }: {
        list: Booking[];
        edit: boolean;
        showClient?: boolean;
        showBranch?: boolean;
        onstart?: (b: Booking) => void;
        onchanged: () => void;
    } = $props();

    let changing = $state<Booking | null>(null);
    let historyOf = $state<string | null>(null);
    let changeOpen = $state(false);
    const today = $derived(operationalDate());

    function change(b: Booking) {
        changing = b;
        changeOpen = true;
    }

    async function cancel(b: Booking) {
        if (!confirm(`Cancel ${b.clientName}'s visit (${visitWhen(b)})?`)) return;
        if (await act(() => setBooking(b, { status: "CANCELLED" }), "Visit cancelled")) onchanged();
    }

    const statusText: Record<S["BookingResponse"]["status"], string> = { BOOKED: "", CAME: "Came", CANCELLED: "Cancelled" };
</script>

<ul class="bookings">
    {#each list as b (b.id)}
        <li class:off={b.status !== "BOOKED"} class:now={b.status === "BOOKED" && b.visitDate === today}>
            <div class="row">
                <b class="when">{visitWhen(b)}</b>
                {#if showClient}<a class="ellipsis" href={`#/clients/${b.clientId}`}>{b.clientName}</a>{/if}
                {#if showBranch}<span class="faint">{b.branchName}</span>{/if}
                {#if statusText[b.status]}<span class="mark">{statusText[b.status]}</span>{/if}
            </div>
            {#if b.note}<div class="small note">{b.note}</div>{/if}
            <div class="faint small">
                {whoText(b)}{#if b.clientPhone && showClient}
                    · {b.clientPhone}{/if}
            </div>
            <div class="row acts">
                {#if edit && b.status === "BOOKED"}
                    {#if onstart && b.visitDate === today}<button class="sm primary" onclick={() => onstart(b)}>Start session</button>{/if}
                    <button class="sm" onclick={() => change(b)}>Change</button>
                    <button class="sm" onclick={() => cancel(b)} aria-label={`Cancel ${b.clientName}'s visit`}>Cancel visit</button>
                {/if}
                <button class="sm" onclick={() => (historyOf = historyOf === b.id ? null : b.id)} aria-expanded={historyOf === b.id}>History</button>
            </div>
            {#if historyOf === b.id}<History table="visit_booking" id={b.id} start />{/if}
        </li>
    {/each}
</ul>

<BookVisit bind:open={changeOpen} booking={changing} onsaved={onchanged} />

<style>
    .bookings {
        list-style: none;
        margin: 0;
        padding: 0;
    }
    li {
        padding: 6px 10px;
        border-bottom: 1px solid var(--line);
    }
    li.now {
        border-left: 3px solid var(--accent);
    }
    li.off {
        opacity: 0.6;
    }
    .row {
        gap: 8px;
        align-items: baseline;
    }
    .when {
        white-space: nowrap;
    }
    .note {
        overflow-wrap: anywhere;
    }
    .acts {
        margin-top: 4px;
        gap: 6px;
    }
</style>
