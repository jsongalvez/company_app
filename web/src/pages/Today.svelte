<script module lang="ts">
    // Opened folds stay open while the app is open; a fresh start folds them again.
    const unfolded = $state({ roster: false, booked: false });
</script>

<script lang="ts">
    import { MediaQuery } from "svelte/reactivity";
    import { api, ok, BASE_STATUS, type S } from "../api/client";
    import { app, can, canRead, branchName } from "../lib/app.svelte";
    import { today, refreshDay, clockIn, clockOut, selectBranch, onDutyAt, markMember } from "../lib/today.svelte";
    import { Query, seed, wrote, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act } from "../lib/act.svelte";
    import { askReason } from "../lib/ask.svelte";
    import { go, route } from "../lib/router.svelte";
    import { peso, time, label, sum, day, shortDay, clientName, operationalDate, addDays } from "../lib/format";
    import { allowedTargets, type Status } from "../lib/rules";
    import { discard, heldMarks, outbox, pendingSales, pendingSessions, retry, sessionsSeen, type Standing } from "../lib/outbox.svelte";
    import { findLocal } from "../lib/clientdir.svelte";
    import { changed } from "../lib/changed";
    import type { Booking } from "../lib/bookings";
    import State from "../components/State.svelte";
    import NewSession from "../components/NewSession.svelte";
    import BookVisit from "../components/BookVisit.svelte";
    import Bookings from "../components/Bookings.svelte";
    import ProductSale from "../components/ProductSale.svelte";
    import Page from "../ui/Page.svelte";
    import Button from "../ui/Button.svelte";
    import Section from "../ui/Section.svelte";
    import Rows from "../ui/Rows.svelte";
    import Row from "../ui/Row.svelte";
    import Notice from "../ui/Notice.svelte";
    import Dock from "../ui/Dock.svelte";
    import Mark from "../ui/Mark.svelte";

    const bid = $derived(app.branchId);
    // Staff enter yesterday's sessions and sales the next day: ‹ opens an earlier day to add to.
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
    /** Start reading a day as the finger goes down on ‹ or ›, ahead of the tap. */
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
    type Roster = Awaited<ReturnType<ReturnType<typeof load.roster>["fn"]>>;
    const roster = new Query<Roster>();
    /** The roster with a just-confirmed mark shown before the server has answered. */
    const rosterShown = $derived(roster.data?.map((m) => (today.marking?.userId === m.userId ? { ...m, present: today.marking.present } : m)));
    const presentNames = $derived((rosterShown ?? []).filter((m) => m.present).map((m) => m.displayName));
    const booked = new Query<Booking[]>();
    const earlierWaiting = new Query<string[]>();
    /** The on-duty list is for the branch's own staff. */
    const member = $derived(app.branches.some((b) => b.branchId === bid && !!b.assignmentId));
    /** Members and relief staff of this branch clock in here. */
    const staffHere = $derived(app.branches.some((b) => b.branchId === bid));
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

    const phone = new MediaQuery("max-width: 1000px");
    const rosterOpen = $derived(!phone.current || unfolded.roster);
    const bookedOpen = $derived(!phone.current || unfolded.booked);
    // A mark made with no signal shows at once, flagged as not sent yet.
    $effect(() => {
        if (dash.data) sessionsSeen(new Set(dash.data.sessions.map((s) => s.id)));
    });
    const sessions = $derived.by(() => {
        void outbox.items.length;
        const rows = isToday ? dash.data?.sessions : (pastList.data ?? (switching ? heldRows : undefined));
        const marks = heldMarks();
        const fromServer = (rows ?? []).map((s) => {
            const h = marks.get(s.id);
            return h && h !== s.sessionStatus
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
                held: p.state === "waiting",
                local: p.state === "sent" ? null : { state: p.state, item: p.id, failed: p.failed },
            }));
        return [...fromServer, ...pending];
    });
    type Shown = (typeof sessions)[number];

    type Local = { state: Exclude<Standing, "sent">; item: string; failed?: string } | null;
    /** The server picks a held session's type when it lands, so the row has none yet. */
    function heldRow(
        at: string,
        b: Record<string, unknown>,
    ): Omit<S["DashboardSessionResponse"], "sessionType"> & { sessionType?: S["DashboardSessionResponse"]["sessionType"]; held: boolean; local: Local } {
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
            local: null,
        };
    }
    const live = $derived(sessions.filter((s) => !s.isVoided));
    const completed = $derived(live.filter((s) => s.sessionStatus === "COMPLETED"));
    const waiting = $derived(live.filter((s) => s.sessionStatus === "PENDING"));
    /** Everything no longer waiting, newest first. */
    const logged = $derived(
        sessions.filter((s) => s.isVoided || s.sessionStatus !== "PENDING").sort((a, b) => (b.bookedAt ?? "").localeCompare(a.bookedAt ?? "")),
    );
    const income = $derived(sum(completed.map((s) => s.finalPrice)));
    const tally = $derived.by(() => {
        const n = (st: Status) => live.filter((s) => s.sessionStatus === st).length;
        const voided = sessions.length - live.length;
        return [
            `${completed.length} done`,
            `${waiting.length} waiting`,
            n("NO_SHOW") && `${n("NO_SHOW")} no-show`,
            n("CANCELLED") && `${n("CANCELLED")} cancelled`,
            voided && `${voided} voided`,
        ]
            .filter(Boolean)
            .join(" · ");
    });

    /** After a save or a mark: the lists changed. The day's own status (open, past, remitted) did not. */
    function reloadLists() {
        if (!isToday) {
            // The first add to a day with nothing recorded makes its row, which a sale needs.
            wrote("pastDayLists", bid, date);
            pastDay.refresh();
            pastList.refresh();
            pastSum.refresh();
            return;
        }
        wrote("todayLists", bid);
        dash.refresh();
        roster.refresh();
        booked.refresh();
    }

    /** The R key: everything on screen, including the day's status. */
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
                sessions: dash.data.sessions.map((x) => (x.id === s.id ? { ...x, sessionStatus: r.sessionStatus!, version: r.version ?? x.version } : x)),
            };
            dash.data = next;
            seed(dash.key, next);
        } else reloadLists();
    }

    async function setPresent(m: S["MemberAttendanceResponse"], present: boolean) {
        const mine = m.userId === app.me?.id;
        if (!confirm(`${present ? "Clock in" : "Clock out"} ${mine ? "yourself" : m.displayName}?`)) return;
        await markMember(m.userId, present, bid);
    }

    let clockBusy = $state(false);
    async function clock(change: () => Promise<boolean>) {
        clockBusy = true;
        await change();
        clockBusy = false;
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

    function tone(s: Shown): "ok" | "warn" | "bad" | "plain" {
        return s.isVoided ? "bad" : s.sessionStatus === "COMPLETED" ? "ok" : s.sessionStatus === "NO_SHOW" ? "warn" : "plain";
    }
    function meta(s: Shown, inLog: boolean): string {
        if (s.local?.state === "failed") return s.local.failed ?? "";
        if (s.local?.state === "waiting") return "Sends when back online";
        const who = s.practitioners?.length
            ? s.practitioners.map((p) => p.displayName).join(", ")
            : s.requestedPractitionerName
              ? `Req: ${s.requestedPractitionerName}`
              : "";
        const price = `${peso(s.finalPrice)}${s.finalPrice !== s.basePrice ? "*" : ""}`;
        return [s.sessionType ? label(s.sessionType) : "", who, inLog ? price : s.bookedAt ? `since ${time(s.bookedAt)}` : ""].filter(Boolean).join(" · ");
    }

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

{#snippet sessionRow(s: Shown, inLog: boolean)}
    <Row
        time={inLog ? time(s.bookedAt) : undefined}
        name={s.clientName ?? "(details erased)"}
        meta={meta(s, inLog)}
        href={s.local ? undefined : `#/sessions/${s.id}`}
        struck={s.isVoided}
        dim={switching}
    >
        {#snippet marks()}
            {#if s.isWalkIn}<Mark>Walk-in</Mark>{/if}
            {#if s.local?.state === "failed"}<Mark tone="bad">Couldn't save</Mark>
            {:else if inLog}<Mark tone={tone(s)}>{s.isVoided ? "Voided" : label(s.sessionStatus)}</Mark>{/if}
            {#if s.held && s.local?.state !== "failed"}<Mark tone="warn" title="Waiting on this phone for signal">Not sent</Mark>{/if}
        {/snippet}
        {#snippet end()}
            {#if s.local?.state === "failed"}
                <Button kind="primary" onclick={() => retry(s.local!.item)}>Retry</Button>
                <Button kind="quiet" onclick={() => confirm("Throw this session away? It won't be saved.") && discard(s.local!.item)}>Throw away</Button>
            {:else if s.local?.state === "sending"}
                <span class="faint">Saving…</span>
            {:else if !s.local && !s.isVoided && canMark}
                {#each allowedTargets(s.sessionStatus as Status, s.isWalkIn, false).filter((to) => (inLog ? to === "PENDING" : to === "COMPLETED")) as to (to)}
                    <Button kind={inLog ? "quiet" : "plain"} onclick={() => mark(s, to)}>{to === "COMPLETED" ? "Done" : "Undo"}</Button>
                {/each}
            {/if}
        {/snippet}
    </Row>
{/snippet}

<Page title="Sessions">
    <div class="dateline" data-one-row>
        <Button kind="quiet" aria-label="Previous day" onpointerdown={() => prefetch(addDays(date, -1))} onclick={() => setDate(addDays(date, -1))}>‹</Button>
        <b class="when"
            >{shortDay(date)}{#if isToday}&nbsp;· Today{/if}</b
        >
        {#if !isToday}<Button kind="quiet" onclick={() => setDate(operationalDate())}>Today</Button>{/if}
        <Button kind="quiet" aria-label="Next day" disabled={isToday} onpointerdown={() => prefetch(addDays(date, 1))} onclick={() => setDate(addDays(date, 1))}
            >›</Button
        >
    </div>

    {#if remitted}
        <div class="inset"><Notice tone="warn">{day(date)} is remitted. Changes need coordinator rights and a reason.</Notice></div>
    {:else if isToday && today.day && today.day.status !== "OPEN"}
        <div class="inset">
            <Notice tone="warn"
                >This day is {label(today.day.status)}. Edits need coordinator rights{today.day.status === "REMITTED" ? " and a reason" : ""}.</Notice
            >
        </div>
    {/if}
    {#if !edit && canRead("SEE_DAY")}
        <div class="inset"><Notice>View only. Clock in here (or get relief access) to make changes.</Notice></div>
    {/if}

    <div class="inset sheet">
        <div class="head">
            <div class="k">Session income</div>
            <div class="big" use:changed={(isToday ? dash.data : pastList.data) && income}>{peso(income)}</div>
            <div class="tally">{tally}</div>
        </div>
        <div class="cells">
            <!-- Each sale is changed or deleted on Expenses & pay, which lists the day's sales. -->
            <a href={`#/finance?date=${date}`}>
                <span class="k">Product sales&nbsp;›</span>
                {#if isToday}<span class="v" use:changed={dash.data?.commission.productSalesCount}
                        >{(dash.data?.commission.productSalesCount ?? 0) + pendingSales(today.day?.branchDayId)}</span
                    >
                {:else}<span class="v" use:changed={pastSumShown?.totalProductSales}>{peso(pastSumShown?.totalProductSales ?? 0)}</span>{/if}
            </a>
            <div>
                <span class="k">Product commission</span>
                <span class="v" use:changed={ownCommission}>{peso(ownCommission ?? 0)}</span>
            </div>
        </div>
    </div>

    {#if isToday && (staffHere || today.shift)}
        <section class="inset duty" aria-label="Duty">
            <div class="who">
                {#if today.shift?.branchId === bid}
                    <span class="dot on" aria-hidden="true"></span>On duty{#if today.shift.isRelief}&nbsp;(relief){/if}
                {:else if today.shift}
                    <span class="dot" aria-hidden="true"></span>On duty at {today.shift.branchName}
                {:else}
                    <span class="dot" aria-hidden="true"></span>Not clocked in
                {/if}
                {#if member && roster.data}<span class="names">{presentNames.length ? presentNames.join(" · ") : "Nobody in yet"}</span>{/if}
            </div>
            {#if today.shift}
                <Button onclick={() => clock(clockOut)} disabled={clockBusy}>Clock out</Button>
            {:else if staffHere && today.shiftKnown}
                <Button kind="primary" onclick={() => clock(() => clockIn(bid))} disabled={clockBusy}>Clock in</Button>
            {/if}
        </section>
    {/if}
    {#if isToday && needClockIn}
        <div class="inset">
            <Notice tone="warn">
                {#if today.shift && today.shift.branchId !== bid}You're clocked in at {today.shift.branchName}. Clock out there before clocking in here.
                {:else}Clock in at {branchName(bid)} to see and log today's sessions.{/if}
                {#snippet end()}
                    {#if today.shift && today.shift.branchId !== bid}
                        <Button onclick={() => selectBranch(today.shift!.branchId)}>Go to {today.shift.branchName}</Button>
                    {:else if !staffHere}<Button href="#/relief">Request relief duty</Button>{/if}
                {/snippet}
            </Notice>
        </div>
    {/if}

    {#if isToday && member}
        <Section
            title="Who's clocked in"
            aside="{presentNames.length} of {rosterShown?.length ?? 0} in"
            fold={phone.current ? { open: rosterOpen, toggle: () => (unfolded.roster = !unfolded.roster) } : undefined}
        >
            <div class="inset">
                <State q={roster} empty={!!roster.data && roster.data.length === 0} emptyText="No members assigned." />
            </div>
            {#if roster.data?.length}
                <Rows label="Who's clocked in">
                    {#each rosterShown ?? [] as m (m.assignmentId)}
                        <Row name={m.userId === app.me?.id ? `${m.displayName} (you)` : m.displayName}>
                            {#snippet end()}
                                {#if !edit}
                                    {#if m.present}<Mark tone="ok">Clocked in</Mark>{:else}<span class="faint">Not in</span>{/if}
                                {:else}
                                    <Button onclick={() => setPresent(m, !m.present)} aria-label="{m.present ? 'Clock out' : 'Clock in'} {m.displayName}"
                                        >{m.present ? "Clock out" : "Clock in"}</Button
                                    >
                                {/if}
                            {/snippet}
                        </Row>
                    {/each}
                </Rows>
            {/if}
        </Section>
    {/if}

    {#if isToday && earlierWaiting.data?.length}
        <div class="inset stack" role="status">
            {#each earlierWaiting.data as d (d)}
                <Notice tone="warn" onpointerdown={() => prefetch(d)} onclick={() => setDate(d)}>Still waiting on {shortDay(d)}</Notice>
            {/each}
        </div>
    {/if}

    {#if waiting.length}
        <Section title={isToday ? "Here now" : "Still waiting"} count={waiting.length}>
            <Rows label={isToday ? "Here now" : "Still waiting"}>
                {#each waiting as s (s.id)}{@render sessionRow(s, false)}{/each}
            </Rows>
        </Section>
    {/if}

    {#if isToday}
        <Section
            title="Booked visits"
            count={booked.data?.length ?? 0}
            fold={phone.current ? { open: bookedOpen, toggle: () => (unfolded.booked = !unfolded.booked) } : undefined}
        >
            <div class="inset">
                <State q={booked} empty={!!booked.data && booked.data.length === 0} emptyText="No visits booked." />
            </div>
            {#if booked.data?.length}
                <Bookings list={booked.data} {edit} onstart={onDutyAt(bid) ? startFromBooking : undefined} onchanged={reloadBookings} />
            {/if}
        </Section>
    {/if}

    {#if !(isToday && needClockIn) && (logged.length || !waiting.length)}
        <Section title={isToday ? "Today's log" : "Log"}>
            <div class="inset">
                <State
                    q={isToday
                        ? dash
                        : {
                              loading: pastList.loading && !switching && late,
                              waiting: pastList.waiting && !heldRows,
                              error: pastList.error,
                              refresh: () => pastList.refresh(),
                          }}
                    empty={!!(isToday ? dash.data : pastList.data) && sessions.length === 0}
                    emptyText={isToday ? "No sessions yet today." : `No sessions on ${day(date)}.`}
                />
            </div>
            {#if logged.length}
                <Rows label={isToday ? "Today's log" : "Log"}>
                    {#each logged as s (s.id)}{@render sessionRow(s, true)}{/each}
                </Rows>
            {/if}
        </Section>
    {/if}

    {#if edit}
        <Dock>
            <Button kind="primary" big disabled={!canAdd} onclick={openNew} title="New session (N)">New session</Button>
            <Button big disabled={!(saleDay && (isToday || canAdd))} onclick={() => (saleOpen = true)} aria-label="Product sale">Sale</Button>
            {#if isToday}<Button big onclick={() => (bookOpen = true)} aria-label="Book a visit" title="Book a client for a later day">Book</Button>{/if}
        </Dock>
    {/if}
</Page>

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
    .dateline {
        display: flex;
        align-items: center;
        gap: var(--s1);
        min-height: var(--bar);
        padding: 0 var(--s2);
        background: var(--panel);
        border-bottom: 1px solid var(--line);
    }
    .when {
        flex: 1;
        text-align: center;
        font-weight: 600;
    }
    .inset {
        margin: 0 var(--s4);
    }
    .stack {
        display: flex;
        flex-direction: column;
        gap: var(--s2);
    }
    .sheet {
        background: var(--panel);
        border: 1px solid var(--line);
    }
    .head {
        padding: var(--s4) var(--s4) var(--s3);
        border-bottom: 1px solid var(--line);
    }
    .k {
        font-size: var(--t-meta);
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--text-2);
    }
    .big {
        font-size: var(--t-big);
        font-weight: 600;
        line-height: var(--lh-tight);
        margin-top: var(--s1);
    }
    .tally {
        margin-top: var(--s1);
        color: var(--text-2);
    }
    .cells {
        display: grid;
        grid-template-columns: 1fr 1fr;
    }
    .cells > * {
        display: flex;
        flex-direction: column;
        min-height: var(--tap);
        padding: var(--s3) var(--s4);
        color: inherit;
        text-decoration: none;
    }
    .cells > * + * {
        border-left: 1px solid var(--line);
    }
    .v {
        font-size: var(--t-title);
        font-weight: 600;
    }
    .duty {
        display: flex;
        align-items: center;
        gap: var(--s2);
        min-height: var(--row);
        padding: var(--s2) var(--s2) var(--s2) var(--s4);
        background: var(--panel);
        border: 1px solid var(--line);
    }
    .who {
        flex: 1;
        min-width: 0;
    }
    .names {
        display: block;
        font-size: var(--t-meta);
        color: var(--text-2);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .dot {
        display: inline-block;
        width: var(--dot);
        height: var(--dot);
        margin-right: var(--s2);
        background: var(--line-strong);
    }
    .dot.on {
        background: var(--ok);
    }
    .faint {
        font-size: var(--t-meta);
        color: var(--text-3);
    }
</style>
