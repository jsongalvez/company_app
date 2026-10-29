<script lang="ts">
    import History from "../components/History.svelte";
    import { askReason, keepReason, lastReason } from "../lib/ask.svelte";
    import { api, ok, uuid, type S } from "../api/client";
    import { app, can } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load, type Loader } from "../lib/data";
    import { Action, act } from "../lib/act.svelte";
    import { route, go, viewSetting } from "../lib/router.svelte";
    import { peso, label, day, time, dateTime, operationalDate, addDays, sum } from "../lib/format";
    import { statusClass } from "../lib/rules";
    import { changed } from "../lib/changed";
    import { EXPENSE_CATEGORIES } from "../lib/rules";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";

    const bid = $derived(app.branchId);
    const date = $derived(route.current.query.get("date") ?? operationalDate());
    const isToday = $derived(date === operationalDate());

    // Resolve the branch day for the chosen date. Today's is already known, so it draws with no loading line.
    const todayDay = $derived(isToday && app.today ? { id: app.today.branchDayId, date, status: app.today.status } : undefined);
    const dayQ = new Query<S["RemittanceDayPickerEntryResponse"] | null>();
    $effect(() => {
        if (todayDay) return;
        const l = load.branchDay(bid, date);
        dayQ.load(l.key, l.fn);
    });
    const theDay = $derived(todayDay ?? dayQ.data);
    const dayId = $derived(theDay?.id ?? "");
    const status = $derived(theDay?.status);

    const exp = new Query<S["ExpenseResponse"][]>();
    const comp = new Query<S["CompensationResponse"][]>();
    const allow = new Query<S["AllowanceResponse"][]>();
    const split = new Query<S["CommissionSplitResponse"][]>();
    const summary = new Query<S["DailySalesSummaryResponse"]>();
    // The day's sessions, so past ones can be opened and corrected (feedback 3578107f).
    const sessQ = new Query<S["DashboardSessionResponse"][]>();
    const seeSessions = $derived(can("VIEW_BRANCH_DATA"));
    $effect(() => {
        if (!seeSessions) return;
        const l = load.daySessions(bid, date);
        sessQ.load(l.key, l.fn);
    });
    const daySessions = $derived(sessQ.data ?? []);
    const doneTotal = $derived(sum(daySessions.filter((x) => !x.isVoided && x.sessionStatus === "COMPLETED").map((x) => x.finalPrice)));
    $effect(() => {
        if (!dayId) return;
        // Pay and allowances are only readable with the pay permission.
        const reads = [
            [exp, load.expenses(dayId)],
            [split, load.commissionSplits(dayId)],
        ] as [Query<unknown>, Loader<unknown>][];
        if (pay) reads.push([comp, load.compensations(dayId)], [allow, load.allowances(dayId)]);
        for (const [q, l] of reads) (q as Query<unknown>).load(l.key, l.fn as () => Promise<unknown>);
        const s = load.dailySummary(bid, date);
        summary.load(s.key, s.fn);
    });

    // Names for user ids.
    let people = $state<{ id: string; name: string }[]>([]);
    const nameOf = $derived(Object.fromEntries(people.map((p) => [p.id, p.name])));
    $effect(() => {
        if (!dayId) return;
        const du = load.dayUsers(dayId);
        const mem = load.members(bid);
        Promise.all([pay ? fetchCached(du.key, du.fn).catch(() => []) : [], fetchCached(mem.key, mem.fn).catch(() => [])]).then(([a, b]) => {
            const m = new Map<string, string>();
            for (const u of a as S["BranchDayUserResponse"][]) m.set(u.userId, u.displayName);
            for (const u of b as S["BranchMemberResponse"][]) if (!m.has(u.id)) m.set(u.id, u.displayName);
            people = [...m].map(([id, name]) => ({ id, name })).sort((x, y) => x.name.localeCompare(y.name));
        });
    });

    const closed = $derived(status !== undefined && status !== "OPEN");
    /** Branch members change expenses until the day is remitted; pay and shares on a closed day stay with past-day rights. */
    const pastRights = $derived(can("EDIT_PAST_DAY"));
    const edit = $derived(status === "REMITTED" ? pastRights : can("EDIT_BRANCH_DATA"));
    const pay = $derived(can("ASSIGN_COMPENSATION"));
    const payEdit = $derived(pay && (!closed || pastRights));
    /** The expense whose change history is open, one at a time. */
    let historyOf = $state<string | null>(null);
    // Fetch before opening, so the row opens on its history instead of a Loading line.
    async function openHistory(id: string) {
        if (historyOf === id) return void (historyOf = null);
        const l = load.audit("expense", id);
        await fetchCached(l.key, l.fn).catch(() => undefined);
        historyOf = id;
    }
    // Closed days need a reason for every change. Forms show a reason field; one-tap actions ask for it.
    let reason = $state("");
    function r() {
        keepReason(reason);
        return reason.trim() || null;
    }
    const startReason = () => (reason = closed ? lastReason() : "");

    // Re-read only what the save can change. The totals count expenses, pay and shares, not allowances.
    // Other people's edits to the other lists show when Finance is opened again or the app reloads.
    function refresh(...changed: ("exp" | "comp" | "allow" | "split" | "summary")[]) {
        const lists = { exp, comp, allow, split, summary };
        const keys = { exp: `exp:${dayId}`, comp: `comp:${dayId}`, allow: `allow:${dayId}`, split: `split:${dayId}`, summary: `dsum:${bid}:${date}` };
        invalidate(...changed.map((c) => keys[c]), ...(changed.includes("exp") ? ["audit:expense:"] : []));
        for (const c of changed) lists[c].refresh();
    }

    // ---- Expense dialog ----
    const ea = new Action();
    let eOpen = $state(false);
    let eEdit = $state<S["ExpenseResponse"] | null>(null);
    let eAmount = $state("");
    let eCat = $state<(typeof EXPENSE_CATEGORIES)[number]>("PANTRY");
    let eNotes = $state("");
    function openExpense(e: S["ExpenseResponse"] | null) {
        eEdit = e;
        eAmount = e?.amount ?? "";
        eCat = (e?.category as typeof eCat) ?? "PANTRY";
        eNotes = e?.notes ?? "";
        startReason();
        ea.reset();
        eOpen = true;
    }
    async function saveExpense() {
        const body = { amount: eAmount.trim(), category: eCat, notes: eNotes.trim() || null, reason: r() };
        const done = await ea.run(
            () =>
                eEdit
                    ? ok(
                          api.PATCH("/api/expenses/{expenseId}", {
                              params: { path: { expenseId: eEdit!.id } },
                              body: { ...body, expectedVersion: eEdit!.version },
                          }),
                      )
                    : ok(api.POST("/api/expenses", { body: { ...body, id: uuid(), branchDayId: dayId } })),
            eEdit ? "Expense updated" : "Expense added",
        );
        if (done) {
            eOpen = false;
            refresh("exp", "summary");
        }
    }
    async function deleteExpense(e: S["ExpenseResponse"]) {
        const why = await askReason("Delete expense");
        if (!why) return;
        if (
            await act(
                () =>
                    ok(
                        api.DELETE("/api/expenses/{expenseId}", {
                            params: { path: { expenseId: e.id } },
                            body: { reason: why, expectedVersion: e.version },
                        }),
                    ),
                "Expense deleted",
            )
        )
            refresh("exp", "summary");
    }
    async function restoreExpense(e: S["ExpenseResponse"]) {
        const why = closed ? await askReason("Restore expense") : "";
        if (why === null) return;
        if (
            await act(
                () => ok(api.POST("/api/expenses/{expenseId}/restore", { params: { path: { expenseId: e.id } }, body: { reason: why || null } })),
                "Expense restored",
            )
        )
            refresh("exp", "summary");
    }
    const delVs = viewSetting("deleted", "");
    let showDeleted = $state(delVs.read === "1");
    $effect(() => delVs.keep(showDeleted ? "1" : ""));
    const expenses = $derived((exp.data ?? []).filter((e) => showDeleted || !e.deletedAt));
    const liveExpenses = $derived((exp.data ?? []).filter((e) => !e.deletedAt));

    // ---- Compensation dialog ----
    const ca = new Action();
    let cOpen = $state(false);
    let cEdit = $state<S["CompensationResponse"] | null>(null);
    let cUser = $state("");
    let cAmount = $state("");
    let cNote = $state("");
    function openComp(c: S["CompensationResponse"] | null) {
        cEdit = c;
        cUser = c?.userId ?? "";
        cAmount = c?.amount ?? "";
        cNote = c?.note ?? "";
        startReason();
        ca.reset();
        cOpen = true;
    }
    async function saveComp() {
        const done = await ca.run(
            () =>
                cEdit
                    ? ok(
                          api.PATCH("/api/compensation/{compensationId}", {
                              params: { path: { compensationId: cEdit!.id } },
                              body: { amount: cAmount.trim(), expectedVersion: cEdit!.version, note: cNote.trim() || null, reason: r() },
                          }),
                      )
                    : ok(
                          api.POST("/api/compensation", {
                              body: {
                                  id: uuid(),
                                  workBranchDayId: dayId,
                                  payingBranchDayId: dayId,
                                  userId: cUser,
                                  amount: cAmount.trim(),
                                  note: cNote.trim() || null,
                                  reason: r(),
                              },
                          }),
                      ),
            "Pay saved",
        );
        if (done) {
            cOpen = false;
            refresh("comp", "summary");
        }
    }

    // ---- Allowance dialog ----
    const aa = new Action();
    let aOpen = $state(false);
    let aUser = $state("");
    let aAmount = $state("");
    async function saveAllowance() {
        if (
            await aa.run(
                () => ok(api.POST("/api/allowances", { body: { id: uuid(), branchDayId: dayId, userId: aUser, amount: aAmount.trim(), reason: r() } })),
                "Allowance added",
            )
        ) {
            aOpen = false;
            refresh("allow");
        }
    }

    async function recalc() {
        if (!confirm("Recount this day's shares from its sales and clock-ins? The shares shown now will be replaced.")) return;
        if (await act(() => ok(api.POST("/api/commission/recalculate/{branchDayId}", { params: { path: { branchDayId: dayId } } })), "Shares updated"))
            refresh("split", "summary");
    }

    function setDate(d: string) {
        go("/finance", { ...Object.fromEntries(route.current.query), date: d === operationalDate() ? null : d });
    }
</script>

<PageHead title="Expenses & pay">
    <span class="daynav">
        <button onclick={() => setDate(addDays(date, -1))} aria-label="Previous day">◀</button>
        <input type="date" value={date} max={operationalDate()} onchange={(e) => setDate(e.currentTarget.value || operationalDate())} />
        <button onclick={() => setDate(addDays(date, 1))} disabled={isToday} aria-label="Next day">▶</button>
        <button onclick={() => setDate(operationalDate())} disabled={isToday}>Today</button>
    </span>
</PageHead>

{#if !todayDay}<State q={dayQ} />{/if}
{#if theDay === null}
    <div class="notice">Nothing was recorded on {day(date)}.</div>
{:else if theDay}
    <div class="row mb wrap">
        <b>{day(date)}</b>
        {#if status === "REMITTED"}
            <span class="mark info">Remitted</span>
        {:else if closed}
            <span class="mark warn">Not remitted</span>
        {/if}
        {#if closed}
            <span class="muted">{edit ? "Reason needed" : "Read only"}</span>
        {/if}
    </div>

    {#if summary.data}
        <div class="stats mb">
            <div>
                <div class="k">Session income</div>
                <div class="v" use:changed={summary.data.grossIncome}>{peso(summary.data.grossIncome)}</div>
            </div>
            <div>
                <div class="k">Pay</div>
                <div class="v" use:changed={summary.data.totalCompensation}>{peso(summary.data.totalCompensation)}</div>
            </div>
            <div>
                <div class="k">Expenses</div>
                <div class="v" use:changed={summary.data.totalExpenses}>{peso(summary.data.totalExpenses)}</div>
            </div>
            <div>
                <div class="k">Net</div>
                <div class="v" use:changed={summary.data.netIncome}>{peso(summary.data.netIncome)}</div>
            </div>
            <div>
                <div class="k">Product sales</div>
                <div class="v" use:changed={summary.data.totalProductSales}>{peso(summary.data.totalProductSales)}</div>
            </div>
            <div>
                <div class="k">Commission</div>
                <div class="v" use:changed={summary.data.totalCommission}>{peso(summary.data.totalCommission)}</div>
            </div>
        </div>
    {/if}

    {#if seeSessions}
        <section class="panel mb">
            <header>
                <h2>Sessions</h2>
                <span class="faint">{peso(doneTotal)}</span>
                <span class="spacer"></span>
            </header>
            <State q={sessQ} empty={!!sessQ.data && daySessions.length === 0} emptyText="No sessions on this day." />
            {#if daySessions.length}
                <div class="table-wrap">
                    <table class="grid">
                        <thead
                            ><tr
                                ><th class="hide-sm">Booked</th><th>Client</th><th>Status</th><th class="hide-sm">Practitioners</th><th class="num">Price</th
                                ></tr
                            ></thead
                        >
                        <tbody>
                            {#each daySessions as x (x.id)}
                                <tr class="click" class:struck={x.isVoided} onclick={() => go(`/sessions/${x.id}`)}>
                                    <td class="hide-sm">{time(x.bookedAt)}</td>
                                    <td class="ellipsis">{x.clientName ?? "(details erased)"}</td>
                                    <td
                                        >{#if x.isVoided}<span class="mark bad">Voided</span>{:else}<span class="mark {statusClass(x.sessionStatus)}"
                                                >{label(x.sessionStatus)}</span
                                            >{/if}</td
                                    >
                                    <td class="ellipsis hide-sm">{x.practitioners?.map((p) => p.displayName).join(", ") ?? ""}</td>
                                    <td class="num">{peso(x.finalPrice)}</td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                </div>
            {/if}
        </section>
    {/if}

    <div class="cols">
        <section class="panel">
            <header>
                <h2>Expenses</h2>
                <span class="faint">{peso(sum(liveExpenses.map((e) => e.amount)))}</span>
                <span class="spacer"></span>
                <label class="check faint"><input type="checkbox" bind:checked={showDeleted} />Show deleted</label>
                {#if edit}<button class="sm primary" onclick={() => openExpense(null)}>Add</button>{/if}
            </header>
            <State q={exp} empty={!!exp.data && expenses.length === 0} emptyText="No expenses." />
            {#if expenses.length}
                <table class="grid">
                    <thead><tr><th>Category</th><th>Notes</th><th class="num">Amount</th><th></th></tr></thead>
                    <tbody>
                        {#each expenses as e (e.id)}
                            <tr class:struck={!!e.deletedAt} title={e.deletedAt ? `Deleted ${dateTime(e.deletedAt)}: ${e.deletedReason ?? ""}` : ""}>
                                <td>{label(e.category)}</td>
                                <td class="wrap muted">{e.notes ?? ""}</td>
                                <td class="num">{peso(e.amount)}</td>
                                <td class="right nowrap">
                                    <button class="sm" class:primary={historyOf === e.id} onclick={() => openHistory(e.id)}>History</button>
                                    {#if edit}
                                        {#if e.deletedAt}
                                            <button class="sm" onclick={() => restoreExpense(e)}>Restore</button>
                                        {:else}
                                            <button class="sm" onclick={() => openExpense(e)}>Edit</button>
                                            <button class="sm" onclick={() => deleteExpense(e)} aria-label="Delete">×</button>
                                        {/if}
                                    {/if}
                                </td>
                            </tr>
                            {#if historyOf === e.id}
                                <tr><td colspan="4"><History table="expense" id={e.id} start /></td></tr>
                            {/if}
                        {/each}
                    </tbody>
                </table>
            {/if}
        </section>

        {#if pay}
            <section class="panel">
                <header>
                    <h2>Pay</h2>
                    <span class="faint">{peso(sum((comp.data ?? []).map((c) => c.amount)))}</span>
                    <span class="spacer"></span>
                    {#if payEdit}<button class="sm primary" onclick={() => openComp(null)}>Add</button>{/if}
                </header>
                <State q={comp} empty={!!comp.data && comp.data.length === 0} emptyText="No pay added for this day." />
                {#if comp.data?.length}
                    <table class="grid">
                        <thead
                            ><tr
                                ><th>Person</th><th>Note</th><th class="num">Amount</th>{#if payEdit}<th></th>{/if}</tr
                            ></thead
                        >
                        <tbody>
                            {#each comp.data as c (c.id)}
                                <tr>
                                    <td
                                        >{c.userName ?? nameOf[c.userId] ?? ""}{#if c.workBranchDayId !== c.payingBranchDayId}<span class="mark plain"
                                                >Relief</span
                                            >{/if}</td
                                    >
                                    <td class="wrap muted">{c.note ?? ""}</td>
                                    <td class="num">{peso(c.amount)}</td>
                                    {#if payEdit}<td class="right"><button class="sm" onclick={() => openComp(c)}>Edit</button></td>{/if}
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                {/if}
            </section>

            <section class="panel">
                <header>
                    <h2>Allowances</h2>
                    <span class="faint">{peso(sum((allow.data ?? []).map((a) => a.amount)))}</span>
                    <span class="spacer"></span>
                    {#if payEdit}<button class="sm primary" onclick={() => ((aUser = ""), (aAmount = ""), startReason(), aa.reset(), (aOpen = true))}
                            >Add</button
                        >{/if}
                </header>
                <State q={allow} empty={!!allow.data && allow.data.length === 0} emptyText="No allowances." />
                {#if allow.data?.length}
                    <table class="grid">
                        <tbody>
                            {#each allow.data as a (a.id)}
                                <tr><td>{nameOf[a.userId] ?? ""}</td><td class="num">{peso(a.amount)}</td></tr>
                            {/each}
                        </tbody>
                    </table>
                {/if}
            </section>
        {/if}

        <section class="panel">
            <header>
                <h2>Commission share</h2>
                <span class="spacer"></span>
                {#if closed && pastRights}<button class="sm" onclick={recalc} title="Work the shares out again from this day's sales and clock-ins"
                        >Update shares</button
                    >{/if}
            </header>
            <State q={split} empty={!!split.data && split.data.length === 0} emptyText="No commission to split." />
            {#if split.data?.length}
                <table class="grid">
                    <tbody>
                        {#each split.data as s (s.id)}
                            <tr><td>{nameOf[s.userId] ?? ""}</td><td class="num">{peso(s.amount)}</td></tr>
                        {/each}
                    </tbody>
                </table>
            {/if}
        </section>
    </div>
{/if}

<Dialog bind:open={eOpen} title={eEdit ? "Edit expense" : "Add expense"} width={400} onsubmit={saveExpense} busy={ea.busy} error={ea.error}>
    <div class="form-grid">
        <label class="field"
            >Category
            <select bind:value={eCat}>
                {#each EXPENSE_CATEGORIES as c (c)}<option value={c}>{label(c)}</option>{/each}
            </select>
        </label>
        <label class="field"><span class="req">Amount</span><input class="num" inputmode="decimal" bind:value={eAmount} required /></label>
        <label class="field wide">Notes<input bind:value={eNotes} /></label>
        {#if closed && (eEdit || status === "REMITTED")}<label class="field wide"
                ><span class="req">Reason for change</span><input bind:value={reason} required /></label
            >{/if}
    </div>
</Dialog>

<Dialog bind:open={cOpen} title={cEdit ? "Edit pay" : "Add pay"} width={400} onsubmit={saveComp} busy={ca.busy} error={ca.error}>
    <div class="form-grid">
        <label class="field wide"
            ><span class="req">Person</span>
            <select bind:value={cUser} required disabled={!!cEdit}>
                <option value="" disabled>Select</option>
                {#each people as p (p.id)}<option value={p.id}>{p.name}</option>{/each}
            </select>
        </label>
        <label class="field"><span class="req">Amount</span><input class="num" inputmode="decimal" bind:value={cAmount} required /></label>
        <label class="field wide">Note<input bind:value={cNote} /></label>
        {#if closed}<label class="field wide"><span class="req">Reason for change</span><input bind:value={reason} required /></label>{/if}
    </div>
</Dialog>

<Dialog bind:open={aOpen} title="Add allowance" width={400} onsubmit={saveAllowance} busy={aa.busy} error={aa.error}>
    <div class="form-grid">
        <label class="field wide"
            ><span class="req">Person</span>
            <select bind:value={aUser} required>
                <option value="" disabled>Select</option>
                {#each people as p (p.id)}<option value={p.id}>{p.name}</option>{/each}
            </select>
        </label>
        <label class="field"><span class="req">Amount</span><input class="num" inputmode="decimal" bind:value={aAmount} required /></label>
        {#if closed}<label class="field wide"><span class="req">Reason for change</span><input bind:value={reason} required /></label>{/if}
    </div>
</Dialog>

<style>
    .hint {
        margin: 4px 10px 6px;
        font-size: 12px;
    }
    .mb {
        margin-bottom: 10px;
    }
    .cols {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
        gap: 12px;
        align-items: start;
    }
    @media (max-width: 760px) {
        .cols {
            grid-template-columns: 1fr;
        }
    }
</style>
