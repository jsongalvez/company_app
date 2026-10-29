<script lang="ts">
    import { askReason } from "../lib/ask.svelte";
    import { goBack } from "../lib/router.svelte";
    import { sortConcerns } from "../lib/concerns";
    import { api, ok, uuid, BASE_STATUS, type S } from "../api/client";
    import { app, can } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { act, Action } from "../lib/act.svelte";
    import { peso, dateTime, day, label, clientName } from "../lib/format";
    import { allowedTargets, isCorrection, statusClass, type Status } from "../lib/rules";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";
    import History from "../components/History.svelte";
    import ProductSale from "../components/ProductSale.svelte";
    import SaleList from "../components/SaleList.svelte";

    let { id }: { id: string } = $props();

    const sq = new Query<S["DashboardSessionResponse"]>();
    const cq = new Query<S["ConcernResponse"][]>(load.concerns().key, load.concerns().fn);
    const client = new Query<S["ClientResponse"]>();

    $effect(() => {
        const l = load.session(id, app.branchId);
        sq.load(l.key, l.fn);
    });
    $effect(() => {
        const cid = sq.data?.clientId;
        if (cid) {
            const l = load.client(cid);
            client.load(l.key, l.fn);
        }
    });

    const s = $derived(sq.data);
    // A session is "today" when it is on today's dashboard; the response has no branchDayId.
    const dash = new Query<S["DashboardResponse"]>();
    $effect(() => {
        const l = load.dashboard(app.branchId);
        dash.load(l.key, l.fn);
    });
    const isToday = $derived(!!dash.data?.sessions.some((x) => x.id === id));

    // Who can be added: today's staff (incl. relief) + branch members.
    let staff = $state<{ id: string; name: string }[]>([]);
    $effect(() => {
        // Who clocked in today is readable only with the pay permission; the branch's members cover the rest.
        const dayId = isToday && can("ASSIGN_COMPENSATION") ? app.today?.branchDayId : undefined;
        const du = dayId ? load.dayUsers(dayId) : null;
        const mem = load.members(app.branchId);
        Promise.all([du ? fetchCached(du.key, du.fn).catch(() => []) : Promise.resolve([]), fetchCached(mem.key, mem.fn).catch(() => [])]).then(([a, b]) => {
            const map = new Map<string, string>();
            for (const u of a as S["BranchDayUserResponse"][]) map.set(u.userId, u.displayName);
            for (const u of b as S["BranchMemberResponse"][]) if (!map.has(u.id)) map.set(u.id, u.displayName);
            staff = [...map].map(([id, name]) => ({ id, name })).sort((x, y) => x.name.localeCompare(y.name));
        });
    });
    const voided = $derived(s?.isVoided ?? false);
    const dayStatus = $derived(s?.dayStatus ?? (isToday ? app.today?.status : "PAST"));
    /** A remitted day takes changes only from those who may change remitted days. */
    const locked = $derived(dayStatus === "REMITTED" && !can("EDIT_PAST_DAY"));
    const edit = $derived(!locked && (can("EDIT_BRANCH_DATA") || can("EDIT_PAST_DAY")));
    /** Branch members void and correct marks; relief does not. */
    const correct = $derived(!locked && can("VOID_SESSION"));
    const needReason = $derived(dayStatus !== "OPEN");
    // Past days need a reason for every change: ask for it when the change is made.
    // Gives "" when none is needed, null when the person cancels.
    function why(what: string, need = needReason): Promise<string | null> {
        return need ? askReason(what) : Promise.resolve("");
    }

    function refresh() {
        invalidate(`session:${id}`, "dash:", "dsess:", `audit:session:${id}`);
        sq.refresh();
    }

    async function setStatus(to: Status) {
        if (!s) return;
        const from = s.sessionStatus as Status;
        const rs = await why(to === "PENDING" ? "Undo" : `Mark ${label(to)}`, needReason || isCorrection(from, to));
        if (rs === null) return;
        if (
            await act(
                () =>
                    ok(
                        api.PATCH("/api/sessions/{sessionId}/status", {
                            params: { path: { sessionId: id } },
                            body: { status: to, version: s.version, reason: rs || null },
                            headers: { [BASE_STATUS]: s.sessionStatus },
                        }),
                    ),
                to === "PENDING" ? "Back to Waiting" : `Marked ${label(to)}`,
            )
        )
            refresh();
    }

    // Price
    const pa = new Action();
    let priceOpen = $state(false);
    let price = $state("");
    async function savePrice() {
        if (!s) return;
        const rs = await why("Change price");
        if (rs === null) return;
        if (
            await pa.run(
                () =>
                    ok(
                        api.PATCH("/api/sessions/{sessionId}/final-price", {
                            params: { path: { sessionId: id } },
                            body: { finalPrice: price.trim(), version: s.version, reason: rs || null },
                        }),
                    ),
                "Price updated",
            )
        ) {
            priceOpen = false;
            refresh();
        }
    }

    // Practitioners
    let addPid = $state("");
    async function addPractitioner() {
        if (!addPid) return;
        const rs = await why("Add person");
        if (rs === null) return;
        if (
            await act(() =>
                ok(
                    api.POST("/api/sessions/{sessionId}/practitioners", {
                        params: { path: { sessionId: id } },
                        body: { id: uuid(), practitionerId: addPid, reason: rs || null },
                    }),
                ),
            )
        ) {
            addPid = "";
            refresh();
        }
    }
    async function removePractitioner(p: S["DashboardPractitionerResponse"]) {
        if (!confirm(`Remove ${p.displayName} from this session?`)) return;
        const rs = await why(`Remove ${p.displayName}`);
        if (rs === null) return;
        if (
            await act(() =>
                ok(
                    api.DELETE("/api/sessions/{sessionId}/practitioners/{practitionerId}", {
                        params: { path: { sessionId: id, practitionerId: p.practitionerId } },
                        body: { reason: rs || null },
                    }),
                ),
            )
        )
            refresh();
    }
    async function editRemarks(p: S["DashboardPractitionerResponse"]) {
        const v = prompt(`Remarks for ${p.displayName}`, p.remarks ?? "");
        if (v === null) return;
        const rs = await why(`Remarks for ${p.displayName}`);
        if (rs === null) return;
        if (
            await act(() =>
                ok(
                    api.PATCH("/api/sessions/{sessionId}/practitioners/{practitionerId}", {
                        params: { path: { sessionId: id, practitionerId: p.practitionerId } },
                        body: { remarks: v.trim() || null, reason: rs || null },
                    }),
                ),
            )
        )
            refresh();
    }

    // Concerns: type to find one on the shared list, or add a new one by name.
    let cText = $state("");
    const cTyped = $derived(cText.trim().replace(/\s+/g, " "));
    const cMatches = $derived.by(() => {
        const t = cTyped.toLowerCase();
        const free = (cq.data ?? []).filter((c) => !sessionConcernIds.has(c.id));
        const list = t ? free.filter((c) => c.label.toLowerCase().includes(t)) : free;
        return sortConcerns(list).slice(0, 12);
    });
    const cExact = $derived((cq.data ?? []).find((c) => c.label.toLowerCase() === cTyped.toLowerCase()) ?? null);
    async function addConcern(concernId: string) {
        const rs = await why("Add illness");
        if (rs === null) return;
        if (
            await act(() =>
                ok(api.POST("/api/sessions/{sessionId}/concerns", { params: { path: { sessionId: id } }, body: { concernId, reason: rs || null } })),
            )
        ) {
            cText = "";
            refresh();
        }
    }
    async function addNewConcern() {
        const name = cTyped;
        if (!name) return;
        const rs = await why("Add illness");
        if (rs === null) return;
        if (
            await act(
                () =>
                    ok(
                        api.POST("/api/sessions/{sessionId}/promote-concern", {
                            params: { path: { sessionId: id } },
                            body: { id: uuid(), label: name, reason: rs || null, clearOtherConcerns: false },
                        }),
                    ),
                "Illness added",
            )
        ) {
            cText = "";
            invalidate("concerns");
            cq.refresh();
            refresh();
        }
    }
    function concernEnter(e: KeyboardEvent) {
        if (e.key !== "Enter") return;
        e.preventDefault();
        if (cExact && !sessionConcernIds.has(cExact.id)) addConcern(cExact.id);
        else if (!cExact && cTyped) addNewConcern();
    }
    async function removeConcern(c: S["ConcernResponse"]) {
        const rs = await why("Remove illness");
        if (rs === null) return;
        if (
            await act(() =>
                ok(
                    api.DELETE("/api/sessions/{sessionId}/concerns/{concernId}", {
                        params: { path: { sessionId: id, concernId: c.id } },
                        body: { reason: rs || null },
                    }),
                ),
            )
        )
            refresh();
    }
    let manageList = $state(false);
    async function removeFromList(c: S["ConcernResponse"]) {
        if (!confirm(`Take "${c.label}" off the illness list for everyone? Past sessions keep it.`)) return;
        if (await act(() => ok(api.DELETE("/api/concerns/{concernId}", { params: { path: { concernId: c.id } } })), "Taken off the list")) {
            invalidate("concerns");
            cq.refresh();
        }
    }
    async function promote() {
        const v = prompt("New illness (added to the list for everyone)", s?.otherConcerns ?? "");
        if (!v?.trim()) return;
        const rs = await why("Add illness");
        if (rs === null) return;
        if (
            await act(
                () =>
                    ok(
                        api.POST("/api/sessions/{sessionId}/promote-concern", {
                            params: { path: { sessionId: id } },
                            body: { id: uuid(), label: v.trim(), reason: rs || null },
                        }),
                    ),
                "Illness added",
            )
        ) {
            invalidate("concerns");
            cq.refresh();
            refresh();
        }
    }

    // Void
    const va = new Action();
    let voidOpen = $state(false);
    let voidReason = $state("");
    async function doVoid() {
        const done = await va.run(
            () =>
                voided
                    ? ok(api.POST("/api/sessions/{sessionId}/unvoid", { params: { path: { sessionId: id } }, body: { unvoidedReason: voidReason.trim() } }))
                    : ok(
                          api.POST("/api/sessions/{sessionId}/void", {
                              params: { path: { sessionId: id } },
                              body: { id: uuid(), voidReason: voidReason.trim() },
                          }),
                      ),
            voided ? "Session restored" : "Session voided",
        );
        if (done) {
            voidOpen = false;
            refresh();
        }
    }

    let saleOpen = $state(false);
    let sales = $state<ReturnType<typeof SaleList>>();
    const sessionConcernIds = $derived(new Set(s?.concerns?.map((c) => c.id) ?? []));
</script>

<PageHead title={client.data ? clientName(client.data) : "Session"} sub={s ? `${label(s.sessionType)} · ${dateTime(s.bookedAt)}` : ""}>
    <button onclick={() => goBack()} title="Back to the previous page">‹ Back</button>
    {#if s && edit && !voided}<button onclick={() => (saleOpen = true)}>Product sale</button>{/if}
    {#if s && correct}
        <button class="danger" onclick={() => ((voidReason = ""), va.reset(), (voidOpen = true))}>{voided ? "Restore session" : "Void session"}</button>
    {/if}
</PageHead>

<State q={sq} />

{#if s}
    <div class="cols">
        <div class="col grow">
            <section class="panel">
                <header>
                    <h2>Session</h2>
                    {#if voided}<span class="mark bad">Voided</span>{:else}<span class="mark {statusClass(s.sessionStatus)}">{label(s.sessionStatus)}</span
                        >{/if}
                    {#if s.isWalkIn}<span class="mark plain">Walk-in</span>{/if}
                    <span class="spacer"></span>
                    {#if edit && !voided}
                        {#each allowedTargets(s.sessionStatus as Status, s.isWalkIn, correct) as to (to)}
                            <button class="sm" class:primary={to === "COMPLETED"} onclick={() => setStatus(to)}>{to === "PENDING" ? "Undo" : label(to)}</button>
                        {/each}
                    {/if}
                </header>
                <div class="body">
                    <dl class="kv">
                        <dt>Client</dt>
                        <dd>
                            {#if client.data}<a href="#/clients/{s.clientId}">{clientName(client.data)}</a> · {client.data.gender === "UNKNOWN"
                                    ? ""
                                    : client.data.gender}
                                {client.data.age}{/if}
                        </dd>
                        <dt>Price</dt>
                        <dd>
                            <b>{peso(s.finalPrice)}</b>
                            {#if s.finalPrice !== s.basePrice}<span class="muted">(base {peso(s.basePrice)})</span>{/if}
                            {#if edit && !voided}<button class="sm" onclick={() => ((price = s.finalPrice), pa.reset(), (priceOpen = true))}>Change</button
                                >{/if}
                        </dd>
                        <dt>Booked</dt>
                        <dd>{dateTime(s.bookedAt)}</dd>
                        <dt>Next visit</dt>
                        <dd>{s.nextAppointmentDate ? day(s.nextAppointmentDate) : "None"}</dd>
                        <dt>Remarks</dt>
                        <dd class="pre">{s.remarks ?? ""}</dd>
                        <dt>Other illnesses</dt>
                        <dd>
                            {s.otherConcerns ?? ""}
                            {#if s.otherConcerns && edit && !voided}<button class="sm" onclick={promote}>Add to illness list</button>{/if}
                        </dd>
                    </dl>
                </div>
            </section>

            {#if s.branchDayId}<SaleList bind:this={sales} dayId={s.branchDayId} sessionId={id} edit={edit && !voided} {needReason} />{/if}

            <History table="session" {id} />
        </div>

        <div class="col side">
            <section class="panel">
                <header><h2>Practitioners</h2></header>
                {#if s.practitioners?.length}
                    <table class="grid">
                        <tbody>
                            {#each s.practitioners as p (p.practitionerId)}
                                <tr>
                                    <td class="faint num">{p.slotAtTime}</td>
                                    <td
                                        >{p.displayName}
                                        <div class="faint small">{p.remarks ?? ""}</div></td
                                    >
                                    {#if edit && !voided}
                                        <td class="right nowrap">
                                            <button class="sm" onclick={() => editRemarks(p)}>Remarks</button>
                                            <button class="sm" onclick={() => removePractitioner(p)} aria-label="Remove">×</button>
                                        </td>
                                    {/if}
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                {:else}
                    <div class="body faint">
                        Nobody assigned.{#if s.requestedPractitionerName}
                            Requested: {s.requestedPractitionerName}.{/if}
                    </div>
                {/if}
                {#if edit && !voided}
                    <div class="body row">
                        <select class="grow" bind:value={addPid}>
                            <option value="">Add practitioner…</option>
                            {#each staff.filter((x) => !s.practitioners?.some((p) => p.practitionerId === x.id)) as x (x.id)}<option value={x.id}
                                    >{x.name}</option
                                >{/each}
                        </select>
                        <button onclick={addPractitioner} disabled={!addPid}>Add</button>
                    </div>
                {/if}
            </section>

            <section class="panel">
                <header><h2>Illnesses</h2></header>
                {#if s.concerns?.length}
                    <table class="grid ill">
                        <tbody>
                            {#each s.concerns as c (c.id)}
                                <tr>
                                    <td>{c.label}</td>
                                    {#if edit && !voided}<td class="right x"
                                            ><button class="sm" onclick={() => removeConcern(c)} aria-label="Remove {c.label}" title="Remove from this session"
                                                >×</button
                                            ></td
                                        >{/if}
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                {:else}<div class="body faint">None recorded.</div>{/if}
                {#if edit && !voided}
                    <div class="body col concern-add">
                        <input placeholder="Type an illness, e.g. back pain" aria-label="Add illness" bind:value={cText} onkeydown={concernEnter} />
                        {#if cTyped || cMatches.length}
                            <div class="table-wrap clist">
                                <table class="grid">
                                    <tbody>
                                        {#each cMatches as c (c.id)}
                                            <tr class="click" onclick={() => addConcern(c.id)}><td>{c.label}</td><td class="right faint">Add</td></tr>
                                        {/each}
                                        {#if cTyped && !cExact}
                                            <tr class="click" onclick={addNewConcern}
                                                ><td>Add “{cTyped}” as a new illness</td><td class="right faint">New</td></tr
                                            >
                                        {:else if cExact && sessionConcernIds.has(cExact.id)}
                                            <tr><td class="faint" colspan="2">Already on this session.</td></tr>
                                        {/if}
                                    </tbody>
                                </table>
                            </div>
                        {/if}
                        <div>
                            <button class="sm" onclick={() => (manageList = !manageList)}>{manageList ? "Done" : "Fix the illness list"}</button>
                        </div>
                        {#if manageList}
                            <div class="table-wrap clist">
                                <table class="grid">
                                    <tbody>
                                        {#each cq.data ?? [] as c (c.id)}
                                            <tr
                                                ><td>{c.label}</td><td class="right"
                                                    ><button class="sm" onclick={() => removeFromList(c)} aria-label="Take {c.label} off the list"
                                                        >Remove</button
                                                    ></td
                                                ></tr
                                            >
                                        {/each}
                                    </tbody>
                                </table>
                            </div>
                        {/if}
                    </div>
                {/if}
            </section>
        </div>
    </div>

    <Dialog bind:open={priceOpen} title="Change price" width={340} onsubmit={savePrice} busy={pa.busy} error={pa.error}>
        <label class="field">Final price<input class="num" inputmode="decimal" bind:value={price} required /></label>
        <div class="faint">List price {peso(s.basePrice)}</div>
    </Dialog>

    <Dialog
        bind:open={voidOpen}
        title={voided ? "Restore session" : "Void session"}
        width={400}
        onsubmit={doVoid}
        submitText={voided ? "Restore session" : "Void session"}
        danger={!voided}
        busy={va.busy}
        error={va.error}
    >
        {#if !voided}<p class="consequence">Stops counting toward sales.</p>{/if}
        <label class="field"><span class="req">Reason</span><textarea bind:value={voidReason} required></textarea></label>
    </Dialog>

    <ProductSale
        bind:open={saleOpen}
        sessionId={id}
        dayId={s?.branchDayId ?? null}
        onsaved={() => {
            refresh();
            sales?.reload();
        }}
    />
{/if}

<style>
    /* Fixed columns: the × stays put however long the names are. */
    table.ill {
        table-layout: fixed;
    }
    table.ill td.x {
        width: 48px;
    }
    .concern-add {
        gap: 4px;
    }
    .clist {
        max-height: 220px;
    }
    .cols {
        display: flex;
        gap: 12px;
        align-items: flex-start;
    }
    .side {
        width: 320px;
        flex: none;
    }
    .kv {
        display: grid;
        grid-template-columns: 110px 1fr;
        gap: 6px 10px;
        margin: 0;
    }
    .kv dt {
        color: var(--text-2);
    }
    .kv dd {
        margin: 0;
        display: flex;
        gap: 8px;
        align-items: center;
        flex-wrap: wrap;
    }
    .pre {
        white-space: pre-wrap;
    }
    .small {
        font-size: 12px;
    }
    @media (max-width: 900px) {
        .cols {
            flex-direction: column;
            align-items: stretch;
        }
        .side {
            width: auto;
        }
    }
</style>
