<script module lang="ts">
    // Opened folds stay open while the app is open; a fresh start folds them again.
    const unfolded = $state({ roster: false, booked: false });
</script>

<script lang="ts">
    import { MediaQuery } from "svelte/reactivity";
    import { api, ok, uuid, BASE_STATUS, type S } from "../api/client";
    import { app, can, branchName, refreshBranchDay, clockIn, selectBranch, onDutyAt, showMark, applyStart } from "../lib/app.svelte";
    import { Query, invalidate, seed } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { askReason } from "../lib/ask.svelte";
    import { go, route, viewSetting } from "../lib/router.svelte";
    import { peso, time, label, sum, day, clientName, operationalDate, addDays } from "../lib/format";
    import { allowedTargets, statusClass, type Status } from "../lib/rules";
    import { heldMark, outbox, pendingSessions, sessionsSeen } from "../lib/outbox.svelte";
    import { findLocal } from "../lib/clientdir.svelte";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import NewSession from "../components/NewSession.svelte";
    import BookVisit from "../components/BookVisit.svelte";
    import Bookings from "../components/Bookings.svelte";
    import { fetchCached } from "../lib/cache.svelte";
    import { changed } from "../lib/changed";
    import type { Booking } from "../lib/bookings";
    import ProductSale from "../components/ProductSale.svelte";

    const bid = $derived(app.branchId);
    // Staff enter yesterday's sessions and sales the next day: ◀ opens an earlier day to add to.
    const date = $derived(route.current.query.get("date") ?? operationalDate());
    const isToday = $derived(date === operationalDate());
    function setDate(d: string) {
        go("/today", { ...Object.fromEntries(route.current.query), date: d === operationalDate() ? null : d });
    }
    /** An earlier day's row: its id (none if nothing was recorded) and whether it is remitted. */
    const pastDay = new Query<S["RemittanceDayPickerEntryResponse"] | null>();
    const pastList = new Query<S["DashboardSessionResponse"][]>();
    const pastSum = new Query<S["DailySalesSummaryResponse"]>();
    /** An earlier day's commission shares; only the branch's members (and its relief) may read them. */
    const pastSplit = new Query<S["CommissionSplitResponse"][]>();
    $effect(() => {
        const id = pastDay.data?.id;
        if (isToday || !id || !can("VIEW_COMMISSION", bid, id)) return;
        const l = load.commissionSplits(id);
        pastSplit.load(l.key, l.fn);
    });
    const pastReads = (b: string, d: string) => [load.branchDay(b, d), load.daySessions(b, d), load.dailySummary(b, d)] as const;
    $effect(() => {
        if (isToday) return;
        const [dl, sl, ml] = pastReads(bid, date);
        pastDay.load(dl.key, dl.fn);
        pastList.load(sl.key, sl.fn);
        pastSum.load(ml.key, ml.fn);
    });
    /** Start reading a day as the finger goes down on ◀ or ▶, ahead of the tap. */
    function prefetch(d: string) {
        if (d === operationalDate()) return;
        for (const l of pastReads(bid, d)) fetchCached(l.key, l.fn as () => Promise<unknown>).catch(() => {});
    }
    // Switching days keeps the last list up, dimmed, until the new day's answers: no "Loading…" flash.
    let heldRows = $state<S["DashboardSessionResponse"][]>();
    let heldSum = $state<S["DailySalesSummaryResponse"]>();
    $effect(() => {
        const rows = isToday ? dash.data?.sessions : pastList.data;
        if (rows) heldRows = rows;
        if (!isToday && pastSum.data) heldSum = pastSum.data;
    });
    // With nothing to hold, a quick answer still shows no "Loading…": it waits 300ms first.
    let late = $state(false);
    $effect(() => {
        void date;
        late = false;
        const t = setTimeout(() => (late = true), 300);
        return () => clearTimeout(t);
    });
    const switching = $derived(!isToday && pastList.loading && !!heldRows);
    const pastSumShown = $derived(pastSum.data ?? (pastSum.loading ? heldSum : undefined));
    const remitted = $derived(!isToday && pastDay.data?.status === "REMITTED");
    /** The day a sale is added to: today's, or the earlier day's when it has a row. */
    const saleDay = $derived(isToday ? app.today?.branchDayId : pastDay.data?.id);
    const dash = new Query(load.dashboard(app.branchId).key, load.dashboard(app.branchId).fn);
    /** The caller's own commission: today's from the dashboard, an earlier day's from its shares. */
    const ownCommission = $derived(
        isToday ? dash.data?.commission.amount : sum((pastSplit.data ?? []).filter((s) => s.userId === app.me?.id).map((s) => s.amount)),
    );
    type Roster = Awaited<ReturnType<ReturnType<typeof load.roster>["fn"]>>;
    const roster = new Query<Roster>();
    /** The roster with a just-confirmed mark shown before the server has answered. */
    const rosterShown = $derived(roster.data?.map((m) => (app.marking?.userId === m.userId ? { ...m, present: app.marking.present } : m)));
    const booked = new Query<Booking[]>();
    /** The on-duty list is for the branch's own staff; others get a note instead. */
    const member = $derived(app.branches.some((b) => b.branchId === bid && !!b.assignmentId));
    $effect(() => {
        const l = load.dashboard(bid);
        dash.load(l.key, l.fn);
        const bk = load.bookings(bid);
        booked.load(bk.key, bk.fn);
        if (member) {
            const r = load.roster(bid);
            roster.load(r.key, r.fn);
        }
    });

    const edit = $derived(can("EDIT_BRANCH_DATA"));
    const correct = $derived(can("EDIT_PAST_DAY"));

    type F = "all" | "PENDING" | "COMPLETED" | "other" | "void";
    // Feedback 77b8ea5f: on a phone the side lists sit above today's sessions, so they start folded to their counts.
    const phone = new MediaQuery("max-width: 1000px");
    const rosterOpen = $derived(!phone.current || unfolded.roster);
    const bookedOpen = $derived(!phone.current || unfolded.booked);
    const showVs = viewSetting("show", "all", ["all", "PENDING", "COMPLETED", "other", "void"]);
    let filter = $state<F>(showVs.read as F);
    $effect(() => showVs.keep(filter));
    // A mark made with no signal shows at once, flagged as not sent yet.
    $effect(() => {
        if (dash.data) sessionsSeen(new Set(dash.data.sessions.map((s) => s.id)));
    });
    const sessions = $derived.by(() => {
        void outbox.items.length;
        const rows = isToday ? dash.data?.sessions : (pastList.data ?? (switching ? heldRows : undefined));
        const fromServer = (rows ?? []).map((s) => {
            const h = heldMark(s.id);
            return h && h !== s.sessionStatus
                ? { ...s, sessionStatus: h as S["DashboardSessionResponse"]["sessionStatus"], held: true, unsent: false }
                : { ...s, held: false, unsent: false };
        });
        // New sessions made on this phone show straight away on their day (none given is today), flagged until they're sent, and stay until the server's list has them.
        const have = new Set(fromServer.map((s) => s.id));
        const pending = pendingSessions()
            .filter((p) => p.body.branchId === bid && (p.body.date ?? operationalDate()) === date && !have.has(String(p.body.id)))
            .map((p) => ({ ...heldRow(p.at, p.body), held: p.unsent, unsent: p.unsent }));
        return [...fromServer, ...pending];
    });

    function heldRow(at: string, b: Record<string, unknown>): S["DashboardSessionResponse"] & { held: boolean; unsent: boolean } {
        const cid = String(b.clientId);
        const c = findLocal(cid);
        const price = String(b.finalPrice ?? "0");
        return {
            id: String(b.id),
            clientId: cid,
            clientName: c ? clientName(c) : "New session",
            isWalkIn: !!b.isWalkIn,
            sessionStatus: "PENDING" as const,
            basePrice: price,
            finalPrice: price,
            remarks: (b.remarks as string) ?? null,
            otherConcerns: (b.otherConcerns as string) ?? null,
            bookedAt: at,
            nextAppointmentDate: (b.nextAppointmentDate as string) ?? null,
            version: 0,
            isVoided: false,
            practitioners: [],
            concerns: [],
            held: true,
            unsent: true,
        };
    }
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

    /** After a save or a mark: the lists changed. The day's own status (open, past, remitted) did not. */
    function reloadLists() {
        if (!isToday) {
            // The first add to a day with nothing recorded makes its row, which a sale needs.
            invalidate(`bday:${bid}:${date}`, `dsess:${bid}:${date}`, `dsum:${bid}:${date}`);
            pastDay.refresh();
            pastList.refresh();
            pastSum.refresh();
            return;
        }
        invalidate(`dash:${bid}`, `roster:${bid}`, `bookings:${bid}`);
        dash.refresh();
        roster.refresh();
        booked.refresh();
    }

    /** The Refresh button and key: everything on screen, including the day's status. */
    function refresh() {
        reloadLists();
        void refreshBranchDay();
    }

    async function mark(s: S["DashboardSessionResponse"], to: Status) {
        const undo = to === "PENDING";
        // An earlier or closed day asks for the reason, which is also the confirmation.
        const closed = !isToday || (!!app.today && app.today.status !== "OPEN");
        const reason = closed ? await askReason(undo ? "Undo" : `Mark ${label(to)}`) : "";
        if (reason === null) return;
        if (!reason && to !== "COMPLETED" && !undo && !confirm(`Mark ${s.clientName ?? "session"} as ${label(to)}?`)) return;
        let saved: unknown;
        const okd = await act(
            async () =>
                (saved = await ok(
                    api.PATCH("/api/sessions/{sessionId}/status", {
                        params: { path: { sessionId: s.id } },
                        body: { status: to, version: s.version, reason: reason || null },
                        headers: { [BASE_STATUS]: s.sessionStatus },
                    }),
                )),
            undo ? `${s.clientName ?? "Session"}: back to Waiting` : `${s.clientName ?? "Session"}: ${label(to)}`,
        );
        if (!okd) return;
        // The answer is the session as saved: show it without reading the lists again (a round trip on a weak signal).
        const r = saved as Partial<S["SessionResponse"]> | undefined;
        if (isToday && r?.id === s.id && r.sessionStatus && dash.data) {
            const next = {
                ...dash.data,
                sessions: dash.data.sessions.map((x) => (x.id === s.id ? { ...x, sessionStatus: r.sessionStatus!, version: r.version ?? x.version } : x)),
            };
            dash.data = next;
            seed(dash.key, next);
        } else reloadLists();
    }

    async function setPresent(m: S["MemberAttendanceResponse"], present: boolean) {
        const mine = m.userId === app.me?.id;
        if (!confirm(`${present ? "Clock in" : "Clock out"} ${mine ? "yourself" : m.displayName}?`)) return;
        const attendanceId = present ? uuid() : null;
        const undo = showMark(m.userId, present, bid, attendanceId);
        let answer: S["AttendanceMarkResponse"] | undefined;
        const done = await act(async () => {
            answer = await ok(
                api.POST("/api/branches/{branchId}/attendance/marks", {
                    params: { path: { branchId: bid }, query: { withToday: true } },
                    body: { userId: m.userId, present, attendanceId },
                }),
            );
        });
        if (!done) return undo();
        // The answer carries the shift, the day and the roster: the top bar and Today change with it.
        await applyStart(answer?.start);
    }

    const needClockIn = $derived(!!dash.error && /clocked in/i.test(dash.error));

    let newOpen = $state(false);
    let bookOpen = $state(false);
    // "Start session" on a booked visit: the popup opens with that client filled in.
    let startClient = $state<S["ClientResponse"] | null>(null);
    let startBooking = $state<Booking | null>(null);
    async function startFromBooking(bk: Booking) {
        const l = load.client(bk.clientId);
        const c = await fetchCached(l.key, l.fn).catch(() => null);
        if (!c) return;
        startClient = c;
        startBooking = bk;
        newOpen = true;
    }
    function openNew() {
        startClient = null;
        startBooking = null;
        newOpen = true;
    }
    function reloadBookings() {
        const bk = load.bookings(bid);
        booked.load(bk.key, bk.fn, true);
    }
    let saleOpen = $state(false);
    /** Adding needs a clock-in only today; an earlier day takes adds until it is remitted. */
    const canAdd = $derived(edit && (isToday ? onDutyAt(bid) : !remitted || correct));
    /** Anyone in the branch marks an earlier day's sessions, with a reason, until it is remitted. */
    const canMark = $derived(edit && (isToday || !remitted || correct));

    // Keyboard: N = new session, R = refresh (when not typing).
    function onKey(e: KeyboardEvent) {
        const t = e.target as HTMLElement;
        if (e.ctrlKey || e.metaKey || e.altKey || /INPUT|SELECT|TEXTAREA/.test(t.tagName) || document.querySelector("dialog[open]")) return;
        if (e.key === "n" && canAdd) {
            e.preventDefault();
            openNew();
        } else if (e.key === "r") refresh();
    }
</script>

<svelte:window onkeydown={onKey} />

<PageHead title="Sessions">
    <span class="daynav">
        <button onpointerdown={() => prefetch(addDays(date, -1))} onclick={() => setDate(addDays(date, -1))} aria-label="Previous day">◀</button>
        <input type="date" value={date} max={operationalDate()} onchange={(e) => setDate(e.currentTarget.value || operationalDate())} />
        <button onpointerdown={() => prefetch(addDays(date, 1))} onclick={() => setDate(addDays(date, 1))} disabled={isToday} aria-label="Next day">▶</button>
        {#if !isToday}<button onclick={() => setDate(operationalDate())}>Today</button>{/if}
    </span>
    <button onclick={refresh} title="Refresh (R)">Refresh</button>
    {#if edit && isToday}<button onclick={() => (bookOpen = true)} title="Book a client for a later day">Book a visit</button>{/if}
    {#if edit && saleDay && (isToday || canAdd)}<button onclick={() => (saleOpen = true)}>Product sale</button>{/if}
    {#if canAdd}
        <button class="primary" onclick={openNew} title="New session (N)">New session</button>
    {/if}
</PageHead>

{#if remitted}
    <div class="notice warn mb">{day(date)} is remitted. Changes need coordinator rights and a reason.</div>
{:else if isToday && app.today && app.today.status !== "OPEN"}
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
        <div class="v" use:changed={(isToday ? dash.data : pastList.data) && sum(completed.map((s) => s.finalPrice))}>
            {peso(sum(completed.map((s) => s.finalPrice)))}
        </div>
    </div>
    <div>
        <div class="k">Product commission</div>
        <div class="v" use:changed={ownCommission}>{peso(ownCommission ?? 0)}</div>
    </div>
    <div>
        <!-- Each sale is changed or deleted on Expenses & pay, which lists the day's sales. -->
        <div class="k"><a href={`#/finance?date=${date}`}>Product sales</a></div>
        {#if isToday}<div class="v" use:changed={dash.data?.commission.productSalesCount}>{dash.data?.commission.productSalesCount ?? 0}</div>
        {:else}<div class="v" use:changed={pastSumShown?.totalProductSales}>{peso(pastSumShown?.totalProductSales ?? 0)}</div>{/if}
    </div>
</div>

<div class="layout">
    <section class="col grow">
        <div class="row wrap">
            <div class="seg" role="group" aria-label="Filter sessions">
                {#each [["all", "All"], ["PENDING", "Waiting"], ["COMPLETED", "Done"], ["other", "No-show"], ["void", "Voided"]] as [k, t] (k)}
                    <button class:on={filter === k} aria-pressed={filter === k} onclick={() => (filter = k as F)}
                        >{t} <span class="faint">{counts[k as F]}</span></button
                    >
                {/each}
            </div>
        </div>

        {#if isToday && edit && !needClockIn && !onDutyAt(bid)}
            <div class="notice row mb">
                <span class="grow">Clock in at {branchName(bid)} to add new sessions.</span>
                {#if !app.shift && app.branches.some((b) => b.branchId === bid)}<button class="primary" onclick={() => clockIn(bid)}>Clock in</button>{/if}
            </div>
        {/if}
        {#if isToday && needClockIn}
            <div class="notice row">
                {#if app.shift && app.shift.branchId !== bid}
                    <span class="grow">You're clocked in at {app.shift.branchName}. Clock out there before clocking in here.</span>
                    <button onclick={() => selectBranch(app.shift!.branchId)}>Go to {app.shift.branchName}</button>
                {:else}<span class="grow">Clock in at {branchName(bid)} to see and log today's sessions.</span>{/if}
                {#if app.shift && app.shift.branchId !== bid}{:else if app.branches.some((b) => b.branchId === bid)}<button
                        class="primary"
                        onclick={() => clockIn(bid)}>Clock in</button
                    >{:else}<a href="#/relief">Request relief duty</a>{/if}
            </div>
        {:else}
            <State
                q={isToday
                    ? dash
                    : {
                          loading: pastList.loading && !switching && late,
                          waiting: pastList.waiting && !heldRows,
                          error: pastList.error,
                          refresh: () => pastList.refresh(),
                      }}
                empty={!!(isToday ? dash.data : pastList.data) && shown.length === 0}
                emptyText={sessions.length ? "No sessions match this filter." : isToday ? "No sessions yet today." : `No sessions on ${day(date)}.`}
            />
        {/if}

        {#if shown.length}
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
                            <tr class:click={!s.unsent} class:struck={s.isVoided} onclick={() => !s.unsent && go(`/sessions/${s.id}`)}>
                                <td class="hide-sm">{time(s.bookedAt)}</td>
                                <td class="ellipsis name lead">
                                    {s.clientName ?? "(details erased)"}
                                    {#if s.isWalkIn}<span class="mark plain">Walk-in</span>{/if}
                                </td>
                                <td class="muted hide-sm">{label(s.sessionType)}</td>
                                <td>
                                    {#if s.isVoided}<span class="mark bad">Voided</span>{:else}<span class="mark {statusClass(s.sessionStatus)}"
                                            >{label(s.sessionStatus)}</span
                                        >{#if s.held}<span class="mark warn" title="Waiting on this phone for signal">Not sent</span>{/if}{/if}
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
                                        {#if s.unsent}<span class="faint">Sends when back online</span>
                                        {:else if !s.isVoided}
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
                    {#if filter === "all" && live.length > 1}
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
        {/if}
    </section>

    {#if isToday}<aside class="panel roster">
            {#if member}
                <header>
                    <h2>
                        {#if phone.current}<button class="fold" aria-expanded={rosterOpen} onclick={() => (unfolded.roster = !unfolded.roster)}
                                >Who's clocked in</button
                            >{:else}Who's clocked in{/if}
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
                                    <td class="right nowrap">
                                        {#if !edit}
                                            {#if m.present}<span class="mark ok">Clocked in</span>{:else}<span class="faint">Not in</span>{/if}
                                        {:else}
                                            <button
                                                class="sm clockbtn"
                                                class:ok={m.present}
                                                onclick={() => setPresent(m, !m.present)}
                                                aria-label="{m.present ? 'Clock out' : 'Clock in'} {m.displayName}"
                                                >{m.present ? "Clock out" : "Clock in"}</button
                                            >
                                        {/if}
                                    </td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                {/if}
            {/if}
            <header class:bk-head={member}>
                <h2>
                    {#if phone.current}<button class="fold" aria-expanded={bookedOpen} onclick={() => (unfolded.booked = !unfolded.booked)}
                            >Booked visits</button
                        >{:else}Booked visits{/if}
                </h2>
                <span class="spacer"></span><span class="faint">{booked.data?.length ?? 0}</span>
            </header>
            {#if bookedOpen}<State q={booked} empty={!!booked.data && booked.data.length === 0} emptyText="No visits booked." />{/if}
            {#if bookedOpen && booked.data?.length}
                <Bookings list={booked.data} {edit} onstart={onDutyAt(bid) ? startFromBooking : undefined} onchanged={reloadBookings} />
            {/if}
        </aside>{/if}
</div>

<NewSession
    bind:open={newOpen}
    {startClient}
    booking={startBooking}
    date={isToday ? null : date}
    dayId={isToday ? null : (pastDay.data?.id ?? null)}
    onsaved={() => (reloadLists(), reloadBookings())}
/>
<BookVisit bind:open={bookOpen} onsaved={reloadBookings} />
<ProductSale bind:open={saleOpen} onsaved={reloadLists} day={isToday ? null : { id: saleDay ?? "", date }} />

<style>
    .mb {
        margin-bottom: 10px;
    }
    .dim {
        opacity: 0.6;
    }
    .layout {
        display: flex;
        gap: 12px;
        align-items: flex-start;
    }
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
    /* The whole title is the tap target; the arrow says whether it is open. */
    .fold {
        all: unset;
        cursor: pointer;
        display: block;
        min-height: 32px;
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
    .note {
        margin: 0;
        padding: 8px 10px;
    }
</style>
