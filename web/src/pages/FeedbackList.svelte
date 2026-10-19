<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { act } from "../lib/act.svelte";
    import { dateTime } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";

    type N = S["FeedbackNoteResponse"];
    type F = "NEW" | "DONE";
    let filter = $state<F>("NEW");
    let notes = $state<N[]>([]);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let expanded = $state<string | null>(null);

    async function fetchNotes() {
        loading = true;
        error = null;
        try {
            notes = await ok(api.GET("/api/feedback-notes", { params: { query: { status: filter } } }));
        } catch (e) {
            error = e instanceof Error ? e.message : String(e);
        } finally {
            loading = false;
        }
    }
    $effect(() => {
        void filter;
        void fetchNotes();
    });

    async function setStatus(n: N, status: F) {
        const done = status === "DONE" ? "Marked done" : "Moved back to New";
        if (await act(() => ok(api.PATCH("/api/feedback-notes/{feedbackId}", { params: { path: { feedbackId: n.id } }, body: { status } })), done))
            notes = notes.filter((x) => x.id !== n.id);
    }
</script>

<PageHead title="Feedback inbox" sub="Problems and ideas people sent from the app">
    <div class="seg" role="group" aria-label="Filter">
        <button class:on={filter === "NEW"} onclick={() => (filter = "NEW")}>New</button>
        <button class:on={filter === "DONE"} onclick={() => (filter = "DONE")}>Done</button>
    </div>
</PageHead>

{#if error}<div class="notice bad">{error}</div>{/if}
{#if !loading && !error && notes.length === 0}<div class="faint">{filter === "NEW" ? "Nothing new." : "Nothing marked done yet."}</div>{/if}
{#if notes.length}
    <div class="table-wrap">
        <table class="grid stack">
            <thead><tr><th>When</th><th>From</th><th>Kind</th><th>Message</th><th>Page</th><th></th></tr></thead>
            <tbody>
                {#each notes as n (n.id)}
                    <tr class="click" onclick={() => (expanded = expanded === n.id ? null : n.id)}>
                        <td class="nowrap">{dateTime(n.createdAt)}</td>
                        <td class="lead"
                            >{n.userDisplayName}<span class="show-sm kind"
                                ><span class="mark" class:warn={n.kind === "PROBLEM"}>{n.kind === "PROBLEM" ? "Problem" : "Idea"}</span></span
                            ></td
                        >
                        <td class="hide-sm"><span class="mark" class:warn={n.kind === "PROBLEM"}>{n.kind === "PROBLEM" ? "Problem" : "Idea"}</span></td>
                        <td class="wrap full" class:ellipsis={expanded !== n.id}>
                            {n.message}
                            {#if expanded === n.id && n.details}<pre class="faint details">{n.details}{n.appVersion
                                        ? `\nVersion: ${n.appVersion}`
                                        : ""}</pre>{/if}
                        </td>
                        <td class="muted hide-sm"
                            >{#if n.page}<a href={n.page} onclick={(e) => e.stopPropagation()}>{n.page}</a>{/if}</td
                        >
                        <td class="right">
                            {#if n.status === "NEW"}
                                <button class="sm" onclick={(e) => (e.stopPropagation(), setStatus(n, "DONE"))}>Mark done</button>
                            {:else}
                                <button class="sm" onclick={(e) => (e.stopPropagation(), setStatus(n, "NEW"))}>Reopen</button>
                            {/if}
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
{/if}

<style>
    .kind {
        margin-left: 8px;
        font-weight: normal;
    }
    .details {
        margin: 6px 0 0;
        white-space: pre-wrap;
        font: inherit;
        font-size: 12px;
    }
</style>
