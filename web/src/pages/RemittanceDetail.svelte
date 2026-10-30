<script lang="ts">
    import { api, ok, uuid, ApiError, type S } from "../api/client";
    import { can, branchName, refreshBranchDay, toast } from "../lib/app.svelte";
    import { Query, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action, act } from "../lib/act.svelte";
    import { peso, label, day, dateTime, time, sum } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";

    let { id }: { id: string } = $props();

    const q = new Query<S["RemittanceDetailResponse"]>();
    $effect(() => {
        const l = load.remittance(id);
        q.load(l.key, l.fn);
    });
    const r = $derived(q.data);
    const draft = $derived(r?.status === "DRAFT");
    const send = $derived(!!r && can("SUBMIT_REMITTANCE", r.branchId));
    const editable = $derived(draft && send);
    const lines = $derived((r?.lines ?? []).filter((l) => !l.deletedAt));

    // What can go in: days, and sessions or product sales, in the date range.
    type Day = S["RemittanceDayPickerEntryResponse"];
    type Sess = S["RemittanceSessionPickerEntryResponse"];
    type Sale = S["RemittanceProductSalePickerEntryResponse"];
    let days = $state<Day[]>([]);
    let sessions = $state<Sess[]>([]);
    let sales = $state<Sale[]>([]);
    // "Add everything" adds from these lists, so it waits until they are in.
    let picking = $state(true);
    let pickKey = "";
    $effect(() => {
        if (!r) return;
        const k = `${r.branchId}:${r.dateRangeStart}:${r.dateRangeEnd}:${r.type}`;
        if (k === pickKey) return;
        pickKey = k;
        void loadPicker();
    });
    async function loadPicker() {
        if (!r) return;
        picking = true;
        const p = { path: { branchId: r.branchId }, query: { from: r.dateRangeStart, to: r.dateRangeEnd } };
        days = await ok(api.GET("/api/branches/{branchId}/remittance-days", { params: p })).catch(() => []);
        if (r.type === "PRODUCT") sales = await ok(api.GET("/api/branches/{branchId}/remittance-product-sales", { params: p })).catch(() => []);
        else sessions = await ok(api.GET("/api/branches/{branchId}/remittance-sessions", { params: p })).catch(() => []);
        picking = false;
    }

    const covered = $derived(new Map((r?.dayBreakdowns ?? []).map((b) => [b.branchDayId, b.id])));
    const inLines = $derived(new Set(lines.map((l) => l.sessionId ?? l.productSaleId)));
    const eligibleSessions = $derived(sessions.filter((s) => s.sessionStatus === "COMPLETED"));
    const isProduct = $derived(r?.type === "PRODUCT");
    const itemWord = $derived(isProduct ? "Product sales" : "Sessions");

    /** Step 2 as one checklist: everything that can go in, plus anything already in that no longer shows there. */
    type Item = { id: string; when: string | null; what: string; amount: string; lineId: string | undefined };
    const lineBySource = $derived(new Map(lines.map((l) => [l.sessionId ?? l.productSaleId ?? "", l.id])));
    const items = $derived.by<Item[]>(() => {
        const pick: Item[] = isProduct
            ? sales.map((s) => ({
                  id: s.id,
                  when: s.soldAt,
                  what: `${s.productName} × ${s.quantity}`,
                  amount: s.totalAmountAtTime,
                  lineId: lineBySource.get(s.id),
              }))
            : eligibleSessions.map((s) => ({
                  id: s.id,
                  when: s.bookedAt,
                  what: s.clientName ?? "(details erased)",
                  amount: s.finalPrice,
                  lineId: lineBySource.get(s.id),
              }));
        const shown = new Set(pick.map((i) => i.id));
        const rest = lines
            .filter((l) => !shown.has(l.sessionId ?? l.productSaleId ?? ""))
            .map((l) => ({ id: l.sessionId ?? l.productSaleId ?? l.id, when: null, what: lineName(l), amount: l.amount, lineId: l.id }));
        return [...pick, ...rest];
    });
    function toggleItem(i: Item) {
        if (i.lineId) void removeLine(i.lineId);
        else void addLine({ id: i.id, amount: i.amount });
    }

    function reload() {
        invalidate(`remd:${id}`, `rem:`);
        q.refresh();
        void loadPicker();
    }

    async function toggleDay(d: Day) {
        if (!r) return;
        const bdId = covered.get(d.id);
        const ok_ = bdId
            ? await act(() =>
                  ok(api.DELETE("/api/remittances/{remittanceId}/day-breakdowns/{breakdownId}", { params: { path: { remittanceId: id, breakdownId: bdId } } })),
              )
            : await act(() =>
                  ok(
                      api.POST("/api/remittances/{remittanceId}/day-breakdowns", {
                          params: { path: { remittanceId: id } },
                          body: { id: uuid(), branchDayId: d.id },
                      }),
                  ),
              );
        if (ok_) reload();
    }

    function addLineBody(src: { id: string; amount: string }) {
        return r?.type === "PRODUCT"
            ? { id: uuid(), type: "PRODUCT_SALE" as const, productSaleId: src.id, sessionId: null, amount: src.amount }
            : { id: uuid(), type: "SESSION" as const, sessionId: src.id, productSaleId: null, amount: src.amount };
    }
    async function addLine(src: { id: string; amount: string }) {
        if (await act(() => ok(api.POST("/api/remittances/{remittanceId}/lines", { params: { path: { remittanceId: id } }, body: addLineBody(src) }))))
            reload();
    }
    async function removeLine(lineId: string) {
        if (await act(() => ok(api.DELETE("/api/remittances/{remittanceId}/lines/{lineId}", { params: { path: { remittanceId: id, lineId } } })))) reload();
    }

    /** Fill the draft: every day in range, then everything that can be included. */
    let filling = $state(false);
    async function addEverything() {
        if (!r) return;
        filling = true;
        await act(async () => {
            for (const d of days) {
                if (!covered.has(d.id))
                    await ok(
                        api.POST("/api/remittances/{remittanceId}/day-breakdowns", {
                            params: { path: { remittanceId: id } },
                            body: { id: uuid(), branchDayId: d.id },
                        }),
                    );
            }
            const srcs =
                r.type === "PRODUCT"
                    ? sales.map((s) => ({ id: s.id, amount: s.totalAmountAtTime }))
                    : eligibleSessions.map((s) => ({ id: s.id, amount: s.finalPrice }));
            let skipped = 0;
            for (const s of srcs) {
                if (inLines.has(s.id)) continue;
                try {
                    await ok(api.POST("/api/remittances/{remittanceId}/lines", { params: { path: { remittanceId: id } }, body: addLineBody(s) }));
                } catch (e) {
                    // Already in another remittance: leave it there and carry on.
                    if (e instanceof ApiError && e.status === 409) skipped++;
                    else throw e;
                }
            }
            if (skipped) toast(`${skipped} already in another remittance, skipped`);
        });
        filling = false;
        reload();
    }

    // Edit header
    const ha = new Action();
    let hOpen = $state(false);
    let hType = $state<"SESSION" | "PRODUCT">("SESSION");
    let hMethod = $state<"BANK_TRANSFER" | "HANDED_TO_ACCOUNTANT">("BANK_TRANSFER");
    let hFrom = $state("");
    let hTo = $state("");
    function openHeader() {
        if (!r) return;
        ha.reset();
        hType = r.type === "PRODUCT" ? "PRODUCT" : "SESSION";
        hMethod = r.method === "HANDED_TO_ACCOUNTANT" ? "HANDED_TO_ACCOUNTANT" : "BANK_TRANSFER";
        hFrom = r.dateRangeStart;
        hTo = r.dateRangeEnd;
        hOpen = true;
    }
    async function saveHeader() {
        if (!r) return;
        const body = { type: hType, method: hMethod, dateRangeStart: hFrom, dateRangeEnd: hTo, expectedVersion: r.version };
        if (await ha.run(() => ok(api.PATCH("/api/remittances/{remittanceId}", { params: { path: { remittanceId: id } }, body })), "Saved")) {
            hOpen = false;
            reload();
        }
    }

    // Send
    const sa = new Action();
    let sOpen = $state(false);
    async function submit() {
        if (!r) return;
        if (
            await sa.run(
                () => ok(api.POST("/api/remittances/{remittanceId}/submit", { params: { path: { remittanceId: id } }, body: { expectedVersion: r.version } })),
                "Remittance sent",
            )
        ) {
            sOpen = false;
            invalidate("dash:", "dsum");
            reload();
            void refreshBranchDay();
        }
    }

    // Undo (within 48 hours)
    const ua = new Action();
    let uOpen = $state(false);
    let uReason = $state("");
    const undoUntil = $derived(r?.submittedAt ? new Date(new Date(r.submittedAt).getTime() + 48 * 3600_000) : null);
    const canUndo = $derived(!!r && r.status === "SUBMITTED" && send && !!undoUntil && undoUntil.getTime() > Date.now());
    async function undo() {
        if (!r) return;
        const body = { expectedVersion: r.version, reason: uReason.trim() };
        if (await ua.run(() => ok(api.POST("/api/remittances/{remittanceId}/undo", { params: { path: { remittanceId: id } }, body })), "Back to draft")) {
            uOpen = false;
            reload();
            void refreshBranchDay();
        }
    }

    // Changes since sent
    let drift = $state<S["RemittanceDriftResponse"] | null>(null);
    // Once per sent version: a reload first shows the saved copy, which after an undo still says sent,
    // and the server has already dropped that version's snapshot.
    let driftFor = "";
    $effect(() => {
        if (r?.status !== "SUBMITTED" || r.type !== "SESSION") return;
        const k = `${id}:${r.version}`;
        if (k === driftFor) return;
        driftFor = k;
        ok(api.GET("/api/remittances/{remittanceId}/drift", { params: { path: { remittanceId: id } } }))
            .then((d) => (drift = d))
            .catch(() => (drift = null));
    });
    const snap = $derived((r?.snapshot ?? null) as Record<string, string> | null);

    const lineName = (l: S["RemittanceLineResponse"]) => {
        if (l.sessionId) {
            const s = sessions.find((x) => x.id === l.sessionId);
            return s ? `${s.clientName ?? "(details erased)"} · ${time(s.bookedAt)}` : "Session";
        }
        const p = sales.find((x) => x.id === l.productSaleId);
        return p ? `${p.productName} × ${p.quantity}` : "Product sale";
    };
</script>

<State {q} />
{#if r}
    <PageHead title="{label(r.type)} remittance" sub="{branchName(r.branchId)} · {day(r.dateRangeStart)} – {day(r.dateRangeEnd)}">
        <a class="btn" href="#/remittance">All remittances</a>
        {#if editable}<button onclick={openHeader}>Edit</button>{/if}
        {#if canUndo}<button class="danger" onclick={() => (ua.reset(), (uReason = ""), (uOpen = true))}>Undo</button>{/if}
    </PageHead>

    <div class="stats mb">
        <div>
            <div class="k">Status</div>
            <div class="v"><span class="mark {draft ? 'warn' : 'ok'}">{label(r.status)}</span></div>
        </div>
        <div>
            <div class="k">How</div>
            <div class="v">{label(r.method)}</div>
        </div>
        <div>
            <div class="k">Items</div>
            <div class="v">{lines.length}</div>
        </div>
        <div>
            <div class="k">Total</div>
            <div class="v">{peso(r.totalAmount)}</div>
        </div>
        {#if r.submittedAt}<div>
                <div class="k">Sent</div>
                <div class="v sm">{dateTime(r.submittedAt)}</div>
            </div>{/if}
    </div>

    {#if canUndo && undoUntil}<div class="notice mb">Can be undone until {dateTime(undoUntil.toISOString())}.</div>{/if}

    {#if snap}
        <section class="panel mb">
            <header>
                <h2>Frozen totals</h2>
                <span class="spacer"></span><span class="faint">as sent</span>
            </header>
            <table class="grid">
                <thead><tr><th></th><th class="num">Income</th><th class="num">Pay</th><th class="num">Expenses</th><th class="num">Net</th></tr></thead>
                <tbody>
                    <tr
                        ><td>When sent</td><td class="num">{peso(snap.grossIncome)}</td><td class="num">{peso(snap.totalCompensation)}</td><td class="num"
                            >{peso(snap.totalExpenses)}</td
                        ><td class="num"><b>{peso(snap.netIncome)}</b></td></tr
                    >
                    {#if drift}
                        <tr>
                            <td>Now</td><td class="num">{peso(drift.frozen.grossIncome)}</td><td class="num">{peso(drift.currentCompensation)}</td><td
                                class="num">{peso(drift.currentExpenses)}</td
                            ><td class="num"><b>{peso(drift.currentNet)}</b></td>
                        </tr>
                    {/if}
                </tbody>
            </table>
            {#if drift && drift.currentNet !== drift.frozen.netIncome}
                <div class="notice warn">Changed since sent: net is now {peso(drift.currentNet)}, it was {peso(drift.frozen.netIncome)}.</div>
            {/if}
        </section>
    {/if}

    {#if editable}
        <section class="panel mb step">
            <header>
                <h2>1 · Days</h2>
                <span class="spacer"></span><span class="faint">{covered.size} of {days.length}</span>
                <button class="sm" onclick={addEverything} disabled={filling || picking}>{filling ? "Adding…" : "Add all"}</button>
            </header>
            <table class="grid">
                <tbody>
                    {#each days as d (d.id)}
                        <tr class="click" onclick={() => toggleDay(d)}>
                            <td class="chk"><input type="checkbox" checked={covered.has(d.id)} tabindex="-1" aria-label={day(d.date)} /></td>
                            <td>{day(d.date)}</td>
                            <td class="right"
                                >{#if d.status === "REMITTED"}<span class="mark info">Already remitted</span>{/if}</td
                            >
                        </tr>
                    {:else}<tr><td class="faint">No days in this range.</td></tr>{/each}
                </tbody>
            </table>
        </section>
        <section class="panel mb step">
            <header>
                <h2>2 · {itemWord}</h2>
                <span class="spacer"></span><span class="faint">{lines.length} of {items.length}</span>
            </header>
            <table class="grid">
                <tbody>
                    {#each items as i (i.id)}
                        <tr class="click" onclick={() => toggleItem(i)}>
                            <td class="chk"><input type="checkbox" checked={!!i.lineId} tabindex="-1" aria-label={i.what} /></td>
                            <td class="lead">{i.what}</td>
                            <td class="muted hide-sm">{i.when ? dateTime(i.when) : ""}</td>
                            <td class="num">{peso(i.amount)}</td>
                        </tr>
                    {:else}<tr><td class="faint">{isProduct ? "No product sales in range." : "No done sessions in range."}</td></tr>{/each}
                </tbody>
            </table>
        </section>
        <div class="send row">
            <span class="grow"><b>{peso(sum(lines.map((l) => l.amount)))}</b> <span class="faint">{lines.length} items · {covered.size} days</span></span>
            <button class="primary" onclick={() => (sa.reset(), (sOpen = true))} disabled={lines.length === 0 || covered.size === 0}>Send to office</button>
        </div>
    {:else}
        <section class="panel">
            <header>
                <h2>Included</h2>
                <span class="spacer"></span><span class="faint">{lines.length} items</span>
            </header>
            {#if lines.length}
                <table class="grid stack">
                    <tbody>
                        {#each lines as l (l.id)}
                            <tr>
                                <td class="lead">{lineName(l)}</td>
                                <td class="num">{peso(l.amount)}</td>
                            </tr>
                        {/each}
                    </tbody>
                    <tfoot><tr><td>Total</td><td class="num">{peso(sum(lines.map((l) => l.amount)))}</td></tr></tfoot>
                </table>
            {:else}<div class="faint pad">Nothing included.</div>{/if}
        </section>
    {/if}

    <Dialog bind:open={hOpen} title="Edit remittance" width={420} onsubmit={saveHeader} busy={ha.busy} error={ha.error}>
        <div class="form-grid">
            <label class="field"
                >Kind<select bind:value={hType} disabled={lines.length > 0}
                    ><option value="SESSION">Sessions</option><option value="PRODUCT">Product sales</option></select
                ></label
            >
            <label class="field"
                >How<select bind:value={hMethod}
                    ><option value="BANK_TRANSFER">Bank transfer</option><option value="HANDED_TO_ACCOUNTANT">Handed to accountant</option></select
                ></label
            >
            <label class="field"><span class="req">From</span><input type="date" bind:value={hFrom} max={hTo} required /></label>
            <label class="field"><span class="req">To</span><input type="date" bind:value={hTo} min={hFrom} required /></label>
        </div>
    </Dialog>

    <Dialog bind:open={sOpen} title="Send remittance" width={420} onsubmit={submit} submitText="Send to office" busy={sa.busy} error={sa.error}>
        <div>Send {peso(r.totalAmount)} from {lines.length} items, covering {covered.size} days?</div>
        <p class="consequence">Totals freeze. Undo within 48 hours.</p>
    </Dialog>

    <Dialog
        bind:open={uOpen}
        title="Undo remittance"
        width={420}
        onsubmit={undo}
        submitText="Return to draft"
        danger
        busy={ua.busy || !uReason.trim()}
        error={ua.error}
    >
        <label class="field"><span class="req">Reason</span><input bind:value={uReason} required /></label>
    </Dialog>
{/if}

<style>
    .mb {
        margin-bottom: 12px;
    }
    .send {
        position: sticky;
        bottom: 0;
        gap: 10px;
        padding: 10px 12px;
        background: var(--panel, #fff);
        border-top: 1px solid var(--line, #ddd);
    }
    .chk {
        width: 24px;
    }
    .pad {
        padding: 8px;
    }
    .v.sm {
        font-size: 13px;
    }
</style>
