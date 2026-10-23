<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { app, branchName, refreshCaps } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action, act } from "../lib/act.svelte";
    import { day, label, operationalDate, addDays } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";

    const today = operationalDate();
    const bid = $derived(app.branchId);
    // Branch side shows only for branches the user works at (not as relief).
    const member = $derived(app.branches.some((b) => b.branchId === bid && !b.isRelief));

    const mine = new Query(load.reliefMine().key, load.reliefMine().fn);
    const invites = new Query(load.reliefInvitesMine().key, load.reliefInvitesMine().fn);

    const asks = new Query<S["ReliefAccessResponse"][]>();
    const sent = new Query<S["ReliefInviteResponse"][]>();
    $effect(() => {
        if (!member) return;
        const a = load.reliefUpcoming(bid);
        asks.load(a.key, a.fn);
        const s = load.branchInvites(bid);
        sent.load(s.key, s.fn);
    });

    function refresh() {
        invalidate("relief:");
        mine.refresh();
        invites.refresh();
        if (member) {
            asks.refresh();
            sent.refresh();
        }
    }

    const statusClass = (s?: string) => (s === "GRANTED" || s === "ACCEPTED" ? "ok" : s === "PENDING" ? "warn" : "plain");

    // ---- My side ----
    async function withdraw(r: S["ReliefAccessResponse"]) {
        if (!confirm(`Withdraw your relief request for ${r.branchName ?? "this branch"} on ${day(r.date)}?`)) return;
        if (await act(() => ok(api.PATCH("/api/relief-access/{requestId}/cancel", { params: { path: { requestId: r.id } }, body: {} })), "Request withdrawn"))
            refresh();
    }
    async function answer(i: S["ReliefInviteResponse"], yes: boolean) {
        const path = yes ? "/api/relief-invites/{inviteId}/accept" : "/api/relief-invites/{inviteId}/decline";
        if (await act(() => ok(api.POST(path, { params: { path: { inviteId: i.id } } })), yes ? "Invite accepted" : "Invite declined")) {
            refresh();
            if (yes) void refreshCaps();
        }
    }

    // Ask for relief
    const aa = new Action();
    let askOpen = $state(false);
    let options = $state<S["ReliefBranchOptionResponse"][]>([]);
    let askBranch = $state("");
    let askDate = $state(today);
    let askReason = $state("");
    async function openAsk() {
        aa.reset();
        askBranch = "";
        askDate = today;
        askReason = "";
        askOpen = true;
        const l = load.reliefOptions();
        options = await fetchCached(l.key, l.fn).catch(() => []);
    }
    async function ask() {
        const done = await aa.run(
            () =>
                ok(
                    api.POST("/api/relief-access/request", {
                        body: { requestId: uuid(), branchId: askBranch, date: askDate, reason: askReason.trim() || null },
                    }),
                ),
            "Request sent to the branch",
        );
        if (done) {
            askOpen = false;
            refresh();
        }
    }

    // ---- Branch side ----
    async function decide(r: S["ReliefAccessResponse"], yes: boolean) {
        const path = yes ? "/api/relief-access/{requestId}/grant" : "/api/relief-access/{requestId}/deny";
        if (await act(() => ok(api.PATCH(path, { params: { path: { requestId: r.id } }, body: {} })), yes ? "Approved" : "Declined")) refresh();
    }
    async function takeBack(i: S["ReliefInviteResponse"]) {
        const accepted = i.status === "ACCEPTED";
        const msg = accepted
            ? `Cancel ${i.inviteeName}'s relief duty on ${day(i.date)}? They and the branch will be told.`
            : `Take back the invite to ${i.inviteeName}?`;
        if (!confirm(msg)) return;
        const path = accepted ? "/api/relief-invites/{inviteId}/revoke" : "/api/relief-invites/{inviteId}/retract";
        if (await act(() => ok(api.POST(path, { params: { path: { inviteId: i.id } } })), accepted ? "Relief duty cancelled" : "Invite taken back")) refresh();
    }

    // Invite someone
    const ia = new Action();
    let invOpen = $state(false);
    let invDate = $state(addDays(today, 1));
    let invQ = $state("");
    let invWho = $state("");
    let candidates = $state<S["ReliefCandidateResponse"][]>([]);
    let timer: ReturnType<typeof setTimeout> | undefined;
    function findPeople() {
        clearTimeout(timer);
        timer = setTimeout(async () => {
            candidates = await ok(
                api.GET("/api/branches/{branchId}/relief-candidates", {
                    params: { path: { branchId: bid }, query: { q: invQ.trim() || undefined, date: invDate } },
                }),
            ).catch(() => []);
        }, 200);
    }
    function openInvite() {
        ia.reset();
        invQ = "";
        invWho = "";
        invDate = addDays(today, 1);
        invOpen = true;
        findPeople();
    }
    async function invite() {
        const done = await ia.run(
            () =>
                ok(
                    api.POST("/api/branches/{branchId}/relief-invites", {
                        params: { path: { branchId: bid } },
                        body: { inviteeUserId: invWho, date: invDate },
                    }),
                ),
            "Invite sent",
        );
        if (done) {
            invOpen = false;
            refresh();
        }
    }

    const myInvites = $derived((invites.data ?? []).filter((i) => i.invitee === app.me?.id));
    const sentLive = $derived((sent.data ?? []).filter((i) => i.status === "PENDING" || (i.status === "ACCEPTED" && i.date >= today)));
</script>

<PageHead title="Relief duty">
    <button onclick={refresh}>Refresh</button>
    <button class="primary" onclick={openAsk}>Ask for relief</button>
</PageHead>

<div class="cols">
    <section class="panel">
        <header><h2>Invites to you</h2></header>
        <State q={invites} empty={!!invites.data && myInvites.length === 0} emptyText="No invites." />
        {#if myInvites.length}
            <table class="grid stack">
                <tbody>
                    {#each myInvites as i (i.id)}
                        <tr>
                            <td class="lead">{i.branchName}</td>
                            <td>{day(i.date)}</td>
                            <td class="muted">from {i.inviterName}</td>
                            <td><span class="mark {statusClass(i.status)}">{label(i.status)}</span></td>
                            <td class="right nowrap full">
                                {#if i.status === "PENDING"}
                                    <button class="sm primary" onclick={() => answer(i, true)}>Accept</button>
                                    <button class="sm" onclick={() => answer(i, false)}>Decline</button>
                                {/if}
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        {/if}
    </section>

    <section class="panel">
        <header><h2>Your requests</h2></header>
        <State q={mine} empty={!!mine.data && mine.data.length === 0} emptyText="You haven't asked for relief." />
        {#if mine.data?.length}
            <table class="grid stack">
                <tbody>
                    {#each mine.data as r (r.id)}
                        <tr>
                            <td class="lead">{r.branchName ?? branchName(r.branchId ?? "")}</td>
                            <td>{day(r.date)}</td>
                            <td><span class="mark {statusClass(r.requestStatus)}">{label(r.requestStatus)}</span></td>
                            <td class="right full">
                                {#if (r.requestStatus === "PENDING" || r.requestStatus === "GRANTED") && (r.date ?? "") >= today}
                                    <button class="sm" onclick={() => withdraw(r)}>Withdraw</button>
                                {/if}
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        {/if}
    </section>
</div>

{#if member}
    <h2 class="sect">At {branchName(bid)}</h2>
    <div class="cols">
        <section class="panel">
            <header>
                <h2>Requests to work here</h2>
            </header>
            <State q={asks} empty={!!asks.data && asks.data.length === 0} emptyText="No upcoming requests." />
            {#if asks.data?.length}
                <table class="grid stack">
                    <tbody>
                        {#each asks.data as r (r.id)}
                            <tr>
                                <td class="lead">{r.requesterName ?? ""}</td>
                                <td>{day(r.date)}</td>
                                <td><span class="mark {statusClass(r.requestStatus)}">{label(r.requestStatus)}</span></td>
                                <td class="right nowrap full">
                                    {#if r.requestStatus === "PENDING"}
                                        <button class="sm primary" onclick={() => decide(r, true)}>Approve</button>
                                        <button class="sm" onclick={() => decide(r, false)}>Decline</button>
                                    {/if}
                                </td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            {/if}
        </section>

        <section class="panel">
            <header>
                <h2>Invites from this branch</h2>
                <span class="spacer"></span>
                <button class="sm" onclick={openInvite}>Invite someone</button>
            </header>
            <State q={sent} empty={!!sent.data && sentLive.length === 0} emptyText="No open invites." />
            {#if sentLive.length}
                <table class="grid stack">
                    <tbody>
                        {#each sentLive as i (i.id)}
                            <tr>
                                <td class="lead">{i.inviteeName}</td>
                                <td>{day(i.date)}</td>
                                <td><span class="mark {statusClass(i.status)}">{label(i.status)}</span></td>
                                <td class="right full">
                                    <button class="sm" onclick={() => takeBack(i)}>{i.status === "ACCEPTED" ? "Cancel duty" : "Take back"}</button>
                                </td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            {/if}
        </section>
    </div>
{/if}

<Dialog bind:open={askOpen} title="Ask for relief" width={420} onsubmit={ask} submitText="Send request" busy={aa.busy || !askBranch} error={aa.error}>
    <div class="form-grid">
        <label class="field wide"
            ><span class="req">Branch</span>
            <select bind:value={askBranch} required>
                <option value="" disabled>Select</option>
                {#each options as o (o.branchId)}<option value={o.branchId}>{o.branchName}</option>{/each}
            </select>
        </label>
        <label class="field"><span class="req">Day</span><input type="date" bind:value={askDate} min={today} required /></label>
        <label class="field wide">Note<input bind:value={askReason} placeholder="Optional" /></label>
    </div>
</Dialog>

<Dialog bind:open={invOpen} title="Invite someone for relief" width={440} onsubmit={invite} submitText="Send invite" busy={ia.busy || !invWho} error={ia.error}>
    <div class="form-grid">
        <label class="field"
            ><span class="req">Day</span><input type="date" bind:value={invDate} min={addDays(today, 1)} onchange={findPeople} required /></label
        >
        <label class="field">Find person<input type="search" bind:value={invQ} oninput={findPeople} placeholder="Name" /></label>
    </div>
    <div class="pick table-wrap">
        <table class="grid">
            <tbody>
                {#each candidates as c (c.id)}
                    <tr class="click" class:sel={invWho === c.id} onclick={() => (invWho = c.id)}>
                        <td>{c.displayName}</td>
                        <td class="muted">{c.username}</td>
                    </tr>
                {:else}
                    <tr><td class="faint">No one found.</td></tr>
                {/each}
            </tbody>
        </table>
    </div>
</Dialog>

<style>
    .cols {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
        margin-bottom: 12px;
        align-items: start;
    }
    .sect {
        margin: 16px 0 8px;
    }
    .pick {
        max-height: 260px;
    }
    @media (max-width: 900px) {
        .cols {
            grid-template-columns: 1fr;
        }
    }
</style>
