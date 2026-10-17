<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { app, can, branchName, refreshBranchDay, clockIn, selectBranch } from "../lib/app.svelte";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { go } from "../lib/router.svelte";
    import { peso, time, label, sum, day, operationalDate } from "../lib/format";
    import { allowedTargets, statusClass, type Status } from "../lib/rules";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import NewSession from "../components/NewSession.svelte";
    import ProductSale from "../components/ProductSale.svelte";

    const bid = $derived(app.branchId);
    const dash = new Query(load.dashboard(app.branchId).key, load.dashboard(app.branchId).fn);
    type Roster = Awaited<ReturnType<ReturnType<typeof load.roster>["fn"]>>;
    const roster = new Query<Roster>();
    /** The on-duty list is for the branch's own staff; others get a note instead. */
    const member = $derived(app.branches.some((b) => b.branchId === bid && !!b.assignmentId));
    $effect(() => {
        const l = load.dashboard(bid);
        dash.load(l.key, l.fn);
        if (member) {
            const r = load.roster(bid);
            roster.load(r.key, r.fn);
        }
    });

    const edit = $derived(can("EDIT_BRANCH_DATA"));
    const correct = $derived(can("EDIT_PAST_DAY"));

    type F = "all" | "PENDING" | "COMPLETED" | "other" | "void";
    let filter = $state<F>("all");
    const sessions = $derived(dash.data?.sessions ?? []);
    const shown = $derived(
        sessions.filter((s) =>
            filter === "all"
                ? true
                : filter === "void"
                  ? s.isVoided
                  : filter === "other"
                    ? !s.isVoided && (s.sessionStatus === "NO_SHOW" || s.sessionStatus === "CANCELLED")
                    : !s.isVoided && s.sessionStatus === filter,
        ),
    );
    const live = $derived(sessions.filter((s) => !s.isVoided));
    const completed = $derived(live.filter((s) => s.sessionStatus === "COMPLETED"));
    const counts = $derived({
        all: sessions.length,
        PENDING: live.filter((s) => s.sessionStatus === "PENDING").length,
        COMPLETED: completed.length,
        other: live.filter((s) => s.sessionStatus === "NO_SHOW" || s.sessionStatus === "CANCELLED").length,
        void: sessions.length - live.length,
    });

    function refresh() {
        invalidate(`dash:${bid}`, `roster:${bid}`);
        dash.refresh();
        roster.refresh();
        void refreshBranchDay();
    }

    async function mark(s: S["DashboardSessionResponse"], to: Status) {
        if (to !== "COMPLETED" && !confirm(`Mark ${s.clientName ?? "session"} as ${label(to)}?`)) return;
        const okd = await act(
            () =>
                ok(
                    api.PATCH("/api/sessions/{sessionId}/status", {
                        params: { path: { sessionId: s.id } },
                        body: { status: to, version: s.version },
                    }),
                ),
            `${s.clientName ?? "Session"}: ${label(to)}`,
        );
        if (okd) refresh();
    }

    async function setPresent(m: S["MemberAttendanceResponse"], present: boolean) {
        const done = await act(() =>
            ok(
                api.POST("/api/branches/{branchId}/attendance/marks", {
                    params: { path: { branchId: bid } },
                    body: { userId: m.userId, present, attendanceId: present ? uuid() : null },
                }),
            ),
        );
        if (done) {
            invalidate(`roster:${bid}`);
            roster.refresh();
        }
    }

    const needClockIn = $derived(!!dash.error && /clocked in/i.test(dash.error));
    async function startShift() {
        if (await clockIn(bid)) {
            invalidate("");
            refresh();
        }
    }

    let newOpen = $state(false);
    let saleOpen = $state(false);

    // Keyboard: N = new session, R = refresh (when not typing).
    function onKey(e: KeyboardEvent) {
        const t = e.target as HTMLElement;
        if (e.ctrlKey || e.metaKey || e.altKey || /INPUT|SELECT|TEXTAREA/.test(t.tagName) || document.querySelector("dialog[open]")) return;
        if (e.key === "n" && edit) {
            e.preventDefault();
            newOpen = true;
        } else if (e.key === "r") refresh();
    }
</script>

<svelte:window onkeydown={onKey} />

<PageHead title="Today" sub="{branchName(bid)} · {day(operationalDate())}">
    <button onclick={refresh} title="Refresh (R)">Refresh</button>
    {#if edit}
        <button onclick={() => (saleOpen = true)}>Product sale</button>
        <button class="primary" onclick={() => (newOpen = true)} title="New session (N)">New session</button>
    {/if}
</PageHead>

{#if app.today && app.today.status !== "OPEN"}
    <div class="notice warn mb">
        This day is {label(app.today.status)}. Edits need coordinator rights{app.today.status === "REMITTED" ? " and a reason" : ""}.
    </div>
{/if}
{#if !edit && can("VIEW_BRANCH_DATA")}
    <div class="notice mb">View only. Clock in here (or get relief access) to make changes.</div>
{/if}

<div class="stats mb">
    <div>
        <div class="k">Sessions</div>
        <div class="v">{live.length}</div>
    </div>
    <div>
        <div class="k">Done</div>
        <div class="v">{completed.length}</div>
    </div>
    <div>
        <div class="k">Waiting</div>
        <div class="v">{counts.PENDING}</div>
    </div>
    <div>
        <div class="k">Session income</div>
        <div class="v">{peso(sum(completed.map((s) => s.finalPrice)))}</div>
    </div>
    <div>
        <div class="k">Product commission</div>
        <div class="v">{peso(dash.data?.commission.amount ?? 0)}</div>
    </div>
    <div>
        <div class="k">Product sales</div>
        <div class="v">{dash.data?.commission.productSalesCount ?? 0}</div>
    </div>
</div>

<div class="layout">
    <section class="col grow">
        <div class="row wrap">
            <div class="seg" role="group" aria-label="Filter sessions">
                {#each [["all", "All"], ["PENDING", "Waiting"], ["COMPLETED", "Done"], ["other", "No-show"], ["void", "Voided"]] as [k, t] (k)}
                    <button class:on={filter === k} onclick={() => (filter = k as F)}>{t} <span class="faint">{counts[k as F]}</span></button>
                {/each}
            </div>
        </div>

        {#if needClockIn}
            <div class="notice row">
                {#if app.shift && app.shift.branchId !== bid}
                    <span class="grow">You're clocked in at {app.shift.branchName}. Clock out there before clocking in here.</span>
                    <button onclick={() => selectBranch(app.shift!.branchId)}>Go to {app.shift.branchName}</button>
                {:else}<span class="grow">Clock in at {branchName(bid)} to see and log today's sessions.</span>{/if}
                {#if app.shift && app.shift.branchId !== bid}{:else if app.branches.some((b) => b.branchId === bid)}<button class="primary" onclick={startShift}
                        >Clock in</button
                    >{:else}<a href="#/relief">Request relief duty</a>{/if}
            </div>
        {:else}
            <State
                q={dash}
                empty={!!dash.data && shown.length === 0}
                emptyText={sessions.length ? "No sessions match this filter." : "No sessions yet today."}
            />
        {/if}

        {#if shown.length}
            <div class="table-wrap">
                <table class="grid stack">
                    <thead>
                        <tr>
                            <th>Booked</th>
                            <th>Client</th>
                            <th>Type</th>
                            <th>Status</th>
                            <th>Practitioners</th>
                            <th>Concerns</th>
                            <th class="num">Price</th>
                            <th>Next visit</th>
                            {#if edit}<th></th>{/if}
                        </tr>
                    </thead>
                    <tbody>
                        {#each shown as s (s.id)}
                            <tr class="click" class:struck={s.isVoided} onclick={() => go(`/sessions/${s.id}`)}>
                                <td class="hide-sm">{time(s.bookedAt)}</td>
                                <td class="ellipsis name lead">
                                    {s.clientName ?? "(details erased)"}
                                    {#if s.isWalkIn}<span class="mark plain">Walk-in</span>{/if}
                                </td>
                                <td class="muted hide-sm">{label(s.sessionType)}</td>
                                <td>
                                    {#if s.isVoided}<span class="mark bad">Voided</span>{:else}<span class="mark {statusClass(s.sessionStatus)}"
                                            >{label(s.sessionStatus)}</span
                                        >{/if}
                                </td>
                                <td class="ellipsis pr">
                                    {#if s.practitioners?.length}
                                        {s.practitioners.map((p) => p.displayName).join(", ")}
                                    {:else if s.requestedPractitionerName}
                                        <span class="faint">Req: {s.requestedPractitionerName}</span>
                                    {:else}<span class="faint">No practitioner yet</span>{/if}
                                </td>
                                <td class="ellipsis cc muted hide-sm">{s.concerns?.map((c) => c.label).join(", ") ?? ""}</td>
                                <td class="num">
                                    {peso(s.finalPrice)}
                                    {#if s.finalPrice !== s.basePrice}<span class="faint" title="List price {peso(s.basePrice)}">*</span>{/if}
                                </td>
                                <td class="muted hide-sm">{s.nextAppointmentDate ? day(s.nextAppointmentDate) : ""}</td>
                                {#if edit}
                                    <td class="acts full" onclick={(e) => e.stopPropagation()}>
                                        {#if !s.isVoided}
                                            {#each allowedTargets(s.sessionStatus as Status, s.isWalkIn, false) as to (to)}
                                                <button class="sm" class:primary={to === "COMPLETED"} onclick={() => mark(s, to)}
                                                    >{to === "COMPLETED" ? "Done" : label(to)}</button
                                                >
                                            {/each}
                                        {/if}
                                    </td>
                                {/if}
                            </tr>
                        {/each}
                    </tbody>
                    {#if filter === "all" && live.length > 1}
                        <tfoot>
                            <tr>
                                <td colspan="6">{live.length} sessions, {completed.length} done</td>
                                <td class="num">{peso(sum(completed.map((s) => s.finalPrice)))}</td>
                                <td colspan={edit ? 2 : 1}></td>
                            </tr>
                        </tfoot>
                    {/if}
                </table>
            </div>
        {/if}
    </section>

    <aside class="panel roster">
        <header>
            <h2>On duty</h2>
            <span class="spacer"></span><span class="faint">{roster.data?.filter((m) => m.present).length ?? 0} present</span>
        </header>
        {#if !member}
            <p class="faint note">Only staff of this branch see who's on duty.</p>
        {:else}
            <State q={roster} empty={!!roster.data && roster.data.length === 0} emptyText="No members assigned." />
        {/if}
        {#if member && roster.data?.length}
            <table class="grid">
                <tbody>
                    {#each roster.data as m, i (m.assignmentId)}
                        <tr>
                            <td class="faint num slot">{i + 1}</td>
                            <td class="ellipsis"
                                >{m.displayName}{#if m.userId === app.me?.id}<span class="faint">&nbsp;(you)</span>{/if}</td
                            >
                            <td class="right">
                                {#if edit}
                                    <label class="check"
                                        ><input type="checkbox" checked={m.present} onchange={(e) => setPresent(m, e.currentTarget.checked)} />Here</label
                                    >
                                {:else if m.present}<span class="mark ok">Here</span>{/if}
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        {/if}
    </aside>
</div>

<NewSession bind:open={newOpen} onsaved={refresh} />
<ProductSale bind:open={saleOpen} onsaved={refresh} />

<style>
    .mb {
        margin-bottom: 10px;
    }
    .layout {
        display: flex;
        gap: 12px;
        align-items: flex-start;
    }
    .roster {
        width: 260px;
        flex: none;
    }
    .roster table.grid td {
        border-bottom-color: var(--line);
    }
    .slot {
        width: 24px;
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
        margin-left: 4px;
    }
    @media (max-width: 1000px) {
        .layout {
            flex-direction: column-reverse;
            align-items: stretch;
        }
        .roster {
            width: auto;
        }
    }
    .note {
        margin: 0;
        padding: 8px 10px;
    }
</style>
