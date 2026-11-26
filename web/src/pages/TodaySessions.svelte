<script lang="ts">
    import type { Row } from "../lib/today-rows";
    import { go } from "../lib/router.svelte";
    import { peso, time, label, sum, day } from "../lib/format";
    import { allowedTargets, statusClass, type Status } from "../lib/rules";
    import { discard, retry } from "../lib/outbox.svelte";

    let {
        shown,
        live,
        completed,
        all,
        switching,
        canMark,
        mark,
    }: {
        shown: Row[];
        live: Row[];
        completed: Row[];
        /** The "All" filter is on: the footer sums the day. */
        all: boolean;
        switching: boolean;
        canMark: boolean;
        mark: (s: Row, to: Status) => void;
    } = $props();
</script>

<div class="table-wrap" class:dim={switching}>
    <table class="grid stack">
        <thead>
            <tr>
                <th>Booked</th>
                <th>Client</th>
                <th>Type</th>
                <th>Status</th>
                <th>Practitioners</th>
                <th>Illnesses</th>
                <th class="num">Price</th>
                <th>Next visit</th>
                {#if canMark}<th></th>{/if}
            </tr>
        </thead>
        <tbody>
            {#each shown as s (s.id)}
                <tr class:click={!s.local} class:struck={s.isVoided} onclick={() => !s.local && go(`/sessions/${s.id}`)}>
                    <td class="hide-sm">{time(s.bookedAt)}</td>
                    <td class="ellipsis name lead">
                        {s.clientName ?? "(details erased)"}
                        {#if s.isWalkIn}<span class="mark plain">Walk-in</span>{/if}
                    </td>
                    <td class="muted hide-sm">{label(s.sessionType)}</td>
                    <td>
                        {#if s.local?.state === "failed"}
                            <span class="refused" onclick={(e) => e.stopPropagation()} role="presentation">
                                <span class="mark bad">Couldn't save</span>
                                <span class="err">{s.local.failed}</span>
                                <button class="sm primary" onclick={() => retry(s.local!.item)}>Retry</button>
                                <button class="sm ghost" onclick={() => confirm("Throw this session away? It won't be saved.") && discard(s.local!.item)}
                                    >Throw away</button
                                >
                            </span>
                        {:else if s.isVoided}<span class="mark bad">Voided</span>{:else}<span class="mark {statusClass(s.sessionStatus)}"
                                >{label(s.sessionStatus)}</span
                            >{#if s.sessionStatus === "COMPLETED" && s.completedAt}<span class="faint at">{time(s.completedAt)}</span>{/if}{#if s.held}<span
                                    class="mark warn"
                                    title="Waiting on this phone for signal">Not sent</span
                                >{/if}{/if}
                    </td>
                    <td class="ellipsis pr">
                        {#if s.practitioners?.length}
                            {s.practitioners.map((p) => p.displayName).join(", ")}
                        {:else if s.requestedPractitionerName}
                            <span class="faint">Req: {s.requestedPractitionerName}</span>
                        {/if}
                    </td>
                    <td class="ellipsis cc muted hide-sm">{s.concerns?.map((c) => c.label).join(", ") ?? ""}</td>
                    <td class="num">
                        {peso(s.finalPrice)}
                        {#if s.finalPrice !== s.basePrice}<span class="faint" title="List price {peso(s.basePrice)}">*</span>{/if}
                    </td>
                    <td class="muted hide-sm">{s.nextAppointmentDate ? day(s.nextAppointmentDate) : ""}</td>
                    {#if canMark}
                        <td class="acts full" onclick={(e) => e.stopPropagation()}>
                            {#if s.local?.state === "sending"}<span class="faint">Saving…</span>
                            {:else if !s.isVoided && (!s.local || s.local.state === "waiting")}
                                {#each allowedTargets(s.sessionStatus as Status, s.isWalkIn, false) as to (to)}
                                    <button class="sm" class:primary={to === "COMPLETED"} onclick={() => mark(s, to)}
                                        >{to === "COMPLETED" ? "Done" : to === "PENDING" ? "Undo" : label(to)}</button
                                    >
                                {/each}
                            {/if}
                        </td>
                    {/if}
                </tr>
            {/each}
        </tbody>
        {#if all && live.length > 1}
            <tfoot>
                <tr>
                    <td colspan="6">{live.length} sessions, {completed.length} done</td>
                    <td class="num">{peso(sum(completed.map((s) => s.finalPrice)))}</td>
                    <td colspan={canMark ? 2 : 1}></td>
                </tr>
            </tfoot>
        {/if}
    </table>
</div>

<style>
    .refused {
        display: inline-flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.25rem 0.5rem;
        max-width: 100%;
    }
    .dim {
        opacity: 0.6;
    }
    .at {
        margin-left: var(--s1);
    }
    .name {
        max-width: 240px;
    }
    .pr {
        max-width: 200px;
    }
    .cc {
        max-width: 180px;
    }
    .acts {
        text-align: right;
    }
    .acts button {
        margin-left: var(--s1);
    }
</style>
