<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { app, refreshUnread, branchName, selectBranch } from "../lib/app.svelte";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { go } from "../lib/router.svelte";
    import { dateTime, day } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";

    type N = S["NotificationResponse"];
    const l = load.notifications();
    const q = new Query(l.key, l.fn);

    // Older, already-read alerts are paged in from the history endpoint.
    let older = $state<N[]>([]);
    let cursor = $state<string | null | undefined>(undefined);
    let olderBusy = $state(false);
    async function more() {
        olderBusy = true;
        try {
            const r = await ok(api.GET("/api/notifications/history", { params: { query: { cursor: cursor ?? undefined, limit: "50" } } }));
            const seen = new Set([...(q.data ?? []), ...older].map((n) => n.id));
            older = [...older, ...r.entries.filter((n) => !seen.has(n.id))];
            cursor = r.nextCursor ?? null;
        } finally {
            olderBusy = false;
        }
    }

    const rows = $derived([...(q.data ?? []), ...older]);
    const unread = $derived(rows.filter((n) => !n.isRead).length);

    function refresh() {
        invalidate("notif");
        q.refresh();
        void refreshUnread();
    }

    async function markRead(n: N) {
        if (n.isRead) return;
        await act(() => ok(api.PATCH("/api/notifications/{notificationId}/read", { params: { path: { notificationId: n.id } } })));
        n.isRead = true;
        void refreshUnread();
    }

    async function readAll() {
        if (await act(() => ok(api.POST("/api/notifications/read-all")), "All marked read")) refresh();
    }

    /** Open what the alert is about: a session, or the branch's day. */
    async function open(n: N) {
        await markRead(n);
        if (n.branchId && n.branchId !== app.branchId) selectBranch(n.branchId);
        if (n.sessionId) go(`/sessions/${n.sessionId}`);
        else if (n.targetDate) go("/relief");
        else go("/today");
    }
</script>

<PageHead title="Alerts">
    <button onclick={refresh}>Refresh</button>
    {#if unread}<button onclick={readAll}>Mark all read</button>{/if}
</PageHead>

<State {q} empty={!!q.data && rows.length === 0} emptyText="No alerts." />
{#if rows.length}
    <div class="table-wrap">
        <table class="grid stack">
            <thead><tr><th>When</th><th>Branch</th><th>Message</th><th>For day</th><th></th></tr></thead>
            <tbody>
                {#each rows as n (n.id)}
                    <tr class="click" class:dim={n.isRead} onclick={() => open(n)}>
                        <td class="nowrap"
                            >{#if !n.isRead}<span class="mark info">New</span>{/if}
                            {dateTime(n.createdAt)}</td
                        >
                        <td class="muted">{branchName(n.branchId)}</td>
                        <td class="wrap full lead">{n.message}</td>
                        <td class="muted">{n.targetDate ? day(n.targetDate) : ""}</td>
                        <td class="right" onclick={(e) => e.stopPropagation()}>
                            {#if !n.isRead}<button class="sm" onclick={() => markRead(n)}>Mark read</button>{/if}
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
    {#if cursor !== null}
        <div class="more"><button onclick={more} disabled={olderBusy}>{olderBusy ? "Loading…" : "Show older"}</button></div>
    {/if}
{/if}

<style>
    .more {
        padding: 8px 0;
    }
</style>
