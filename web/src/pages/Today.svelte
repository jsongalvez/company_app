<script lang="ts">
    import { MediaQuery } from "svelte/reactivity";
    import { api, ok, BASE_STATUS, type S } from "../api/client";
    import { app, can, canRead, branchName } from "../lib/app.svelte";
    import { today, refreshDay, clockIn, selectBranch, onDutyAt } from "../lib/today.svelte";
    import { Query, saveNow, seed } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { askReason } from "../lib/ask.svelte";
    import { go, route, viewSetting } from "../lib/router.svelte";
    import { peso, label, sum, day, operationalDate, addDays, nowIso } from "../lib/format";
    import type { Status } from "../lib/rules";
    import { heldMarks, outbox, pendingSales, pendingSessions, sessionsSeen } from "../lib/outbox.svelte";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import { later } from "../lib/later.svelte";
    import TodaySessions from "./TodaySessions.svelte";
    import { heldRow } from "../lib/today-rows";
    import TodayAside, { type Roster } from "./TodayAside.svelte";
    import { fetchCached } from "../lib/cache.svelte";
    import { changed } from "../lib/changed";
    import type { Booking } from "../lib/bookings";

    const newSession = later(() => import("../components/NewSession.svelte"));
    const bookVisit = later(() => import("../components/BookVisit.svelte"));
    const productSale = later(() => import("../components/ProductSale.svelte"));
    const NewSession = $derived(newSession.c);
    const BookVisit = $derived(bookVisit.c);
    const ProductSale = $derived(productSale.c);

    // The Site panel's code loads after Today shows; it draws nothing until a site hub answers.
    let SitePanel = $state<typeof import("../components/SitePanel.svelte").default>();
    $effect(() => {
        const t = setTimeout(() => void import("../components/SitePanel.svelte").then((m) => (SitePanel = m.default)), 3_000);
        return () => clearTimeout(t);
    });
    // The dialogs' code loads soon after Today shows, or at once on a tap that opens one.
    $effect(() => {
        const t = setTimeout(() => (newSession.load(), bookVisit.load(), productSale.load()), 3_000);
        return () => clearTimeout(t);
    });
    $effect(() => {
        if (newOpen) newSession.load();
        if (bookOpen) bookVisit.load();
        if (saleOpen) productSale.load();
    });
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
        if (isToday || !id || !canRead("SEE_COMMISSION", { branch: bid, day: id })) return;
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
    const saleDay = $derived(isToday ? today.day?.branchDayId : pastDay.data?.id);
    const dash = new Query(load.dashboard(app.branchId).key, load.dashboard(app.branchId).fn);
    /** The caller's own commission: today's from the dashboard, an earlier day's from its shares. */
    const ownCommission = $derived(
        isToday ? dash.data?.commission.amount : sum((pastSplit.data ?? []).filter((s) => s.userId === app.me?.id).map((s) => s.amount)),
    );
    const roster = new Query<Roster>();
    const booked = new Query<Booking[]>();
    const earlierWaiting = new Query<string[]>();
    /** The on-duty list is for the branch's own staff; others get a note instead. */
    const member = $derived(app.branches.some((b) => b.branchId === bid && !!b.assignmentId));
    $effect(() => {
        const l = load.dashboard(bid);
        dash.load(l.key, l.fn);
        const bk = load.bookings(bid);
        booked.load(bk.key, bk.fn);
        const ew = load.earlierWaiting(bid);
        earlierWaiting.load(ew.key, ew.fn);
        if (member) {
            const r = load.roster(bid);
            roster.load(r.key, r.fn);
        }
    });

    const edit = $derived(can("WORK_DAY"));
    const correct = $derived(can("CHANGE_CLOSED_DAY", { branch: bid }));

    type F = "all" | "PENDING" | "COMPLETED" | "other" | "void";
    const phone = new MediaQuery("max-width: 1000px");
    const showVs = viewSetting("show", "all", ["all", "PENDING", "COMPLETED", "other", "void"]);
    let filter = $state<F>(showVs.read as F);
    $effect(() => showVs.keep(filter));
    // A mark made with no signal shows at once, flagged as not sent yet.
    $effect(() => {
        if (dash.data) sessionsSeen(new Set(dash.data.sessions.map((s) => s.id)), saveNow);
    });
    const sessions = $derived.by(() => {
        void outbox.items.length;
        const rows = isToday ? dash.data?.sessions : (pastList.data ?? (switching ? heldRows : undefined));
        const marks = heldMarks();
        const fromServer = (rows ?? []).map((s) => {
            // Flagged while it waits, even once the row shows it (the queued mark applies to every screen's copy, lib/mutators.ts).
            const h = marks.get(s.id);
            return h
                ? { ...s, sessionStatus: h as S["DashboardSessionResponse"]["sessionStatus"], held: true, local: null }
                : { ...s, held: false, local: null };
        });
        // New sessions made on this phone show the moment they're saved, on their day (none given is today), and stay until
        // the server's list has them. One the server refused stays too, with why, until it is sent again or thrown away.
        const have = new Set(fromServer.map((s) => s.id));
        const pending = pendingSessions()
            .filter((p) => p.body.branchId === bid && (p.body.date ?? operationalDate()) === date && !have.has(String(p.body.id)))
            .map((p) => ({
                ...heldRow(p.at, p.body),
                // A mark made with no signal on a session that is still waiting too: both go out in order.
                sessionStatus: (marks.get(String(p.body.id)) ?? "PENDING") as S["DashboardSessionResponse"]["sessionStatus"],
                held: p.state === "waiting",
                local: p.state === "sent" ? null : { state: p.state, item: p.id, failed: p.failed },
            }));
        return [...fromServer, ...pending];
    });

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
            pastDay.refresh();
            pastList.refresh();
            pastSum.refresh();
            return;
        }
        dash.refresh();
        roster.refresh();
        booked.refresh();
    }

    /** The Refresh button and key: everything on screen, including the day's status. */
    function refresh() {
        reloadLists();
        void refreshDay();
    }

    async function mark(s: Pick<S["DashboardSessionResponse"], "id" | "version" | "clientName" | "sessionStatus">, to: Status) {
        const undo = to === "PENDING";
        // An earlier or closed day asks for the reason, which is also the confirmation.
        const closed = !isToday || (!!today.day && today.day.status !== "OPEN");
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
                sessions: dash.data.sessions.map((x) =>
                    x.id === s.id
                        ? {
                              ...x,
                              sessionStatus: r.sessionStatus!,
                              version: r.version ?? x.version,
                              completedAt: r.sessionStatus === "COMPLETED" ? nowIso() : null,
                          }
                        : x,
                ),
            };
            dash.data = next;
            seed(dash.key, next);
        } else reloadLists();
    }

    // The server refused the list before a clock-in this phone holds: that refusal is out of date.
    const clockRefusal = $derived(!!dash.error && /clocked in/i.test(dash.error));
    const needClockIn = $derived(clockRefusal && !onDutyAt(bid));
    const dashShown = $derived(
        clockRefusal && !needClockIn ? { ...dash, loading: dash.loading, waiting: dash.waiting, error: null, refresh: () => dash.refresh() } : dash,
    );

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

{#if isToday && SitePanel}<SitePanel />{/if}
{#if remitted}
    <div class="notice warn mb">{day(date)} is remitted. Changes need coordinator rights and a reason.</div>
{:else if isToday && today.day && today.day.status !== "OPEN"}
    <div class="notice warn mb">
        This day is {label(today.day.status)}. Edits need coordinator rights{today.day.status === "REMITTED" ? " and a reason" : ""}.
    </div>
{/if}
{#if isToday && earlierWaiting.data?.length}
    <div class="notice warn mb" role="status">
        Still waiting on
        {#each earlierWaiting.data as d (d)}
            <button class="ghost" onpointerdown={() => prefetch(d)} onclick={() => setDate(d)}>{day(d)} ›</button>
        {/each}
    </div>
{/if}
{#if !edit && canRead("SEE_DAY")}
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
    <!-- Each sale is changed or deleted on Expenses & pay, which lists the day's sales. -->
    <a href={`#/finance?date=${date}`}>
        <div class="k">Product sales</div>
        {#if isToday}<div class="v" use:changed={dash.data?.commission.productSalesCount}>
                {(dash.data?.commission.productSalesCount ?? 0) + pendingSales(today.day?.branchDayId)}
            </div>
        {:else}<div class="v" use:changed={pastSumShown?.totalProductSales}>{peso(pastSumShown?.totalProductSales ?? 0)}</div>{/if}
    </a>
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
                {#if !today.shift && app.branches.some((b) => b.branchId === bid)}<button class="primary" onclick={() => clockIn(bid)}>Clock in</button>{/if}
            </div>
        {/if}
        {#if isToday && needClockIn}
            <div class="notice row">
                {#if today.shift && today.shift.branchId !== bid}
                    <span class="grow">You're clocked in at {today.shift.branchName}. Clock out there before clocking in here.</span>
                    <button onclick={() => selectBranch(today.shift!.branchId)}>Go to {today.shift.branchName}</button>
                {:else}<span class="grow">Clock in at {branchName(bid)} to see and log today's sessions.</span>{/if}
                {#if today.shift && today.shift.branchId !== bid}{:else if app.branches.some((b) => b.branchId === bid)}<button
                        class="primary"
                        onclick={() => clockIn(bid)}>Clock in</button
                    >{:else}<a class="btn" href="#/relief">Request relief duty</a>{/if}
            </div>
        {:else}
            <State
                q={isToday
                    ? dashShown
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
            <TodaySessions {shown} {live} {completed} all={filter === "all"} {switching} {canMark} {mark} />
        {/if}
    </section>

    {#if isToday}<TodayAside
            {bid}
            {edit}
            {member}
            phone={phone.current}
            {roster}
            {booked}
            onstart={onDutyAt(bid) ? startFromBooking : undefined}
            onchanged={reloadBookings}
        />{/if}
</div>

{#if NewSession}<NewSession
        bind:open={newOpen}
        {startClient}
        booking={startBooking}
        date={isToday ? null : date}
        dayId={isToday ? null : (pastDay.data?.id ?? null)}
        onsaved={() => (reloadLists(), reloadBookings())}
    />{/if}
{#if BookVisit}<BookVisit bind:open={bookOpen} onsaved={reloadBookings} />{/if}
{#if ProductSale}<ProductSale bind:open={saleOpen} onsaved={reloadLists} day={isToday ? null : { id: saleDay ?? "", date }} />{/if}

<style>
    .mb {
        margin-bottom: 10px;
    }
    .layout {
        display: flex;
        gap: var(--s3);
        align-items: flex-start;
    }
    @media (max-width: 1000px) {
        .layout {
            flex-direction: column-reverse;
            align-items: stretch;
        }
    }
</style>
