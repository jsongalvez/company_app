<script lang="ts">
    import { goBack } from "../lib/router.svelte";
    import { sortConcerns } from "../lib/concerns";
    import { api, ok, uuid, type S } from "../api/client";
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
        const dayId = isToday ? app.today?.branchDayId : undefined;
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
    const dayStatus = $derived(isToday ? app.today?.status : "PAST");
    const edit = $derived(can("EDIT_BRANCH_DATA") || can("EDIT_PAST_DAY"));
    const correct = $derived(can("EDIT_PAST_DAY"));
    const needReason = $derived(dayStatus !== "OPEN");
    let reason = $state("");
    const r = () => (reason.trim() ? reason.trim() : null);
    let reasonBox = $state<HTMLInputElement>();
    // Past days need a reason for every change; say so before anything is sent.
    function hasReason() {
        if (!needReason || r()) return true;
        alert("This day is closed. Write the reason for the change in the reason box first.");
        reasonBox?.focus();
        return false;
    }

    function refresh() {
        invalidate(`session:${id}`, "dash:", "dsess:", `audit:session:${id}`);
        sq.refresh();
    }

    async function setStatus(to: Status) {
        if (!s) return;
        if (!hasReason()) return;
        const from = s.sessionStatus as Status;
        if (isCorrection(from, to) && !r()) {
            alert("A correction needs a reason. Fill in the reason box first.");
            return;
        }
        if (
            await act(
                () =>
                    ok(
                        api.PATCH("/api/sessions/{sessionId}/status", {
                            params: { path: { sessionId: id } },
                            body: { status: to, version: s.version, reason: r() },
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
        if (!hasReason()) return;
        if (
            await pa.run(
                () =>
                    ok(
                        api.PATCH("/api/sessions/{sessionId}/final-price", {
                            params: { path: { sessionId: id } },
                            body: { finalPrice: price.trim(), version: s.version, reason: r() },
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
        if (!hasReason()) return;
        if (
            await act(() =>
                ok(
                    api.POST("/api/sessions/{sessionId}/practitioners", {
                        params: { path: { sessionId: id } },
                        body: { id: uuid(), practitionerId: addPid, reason: r() },
                    }),
                ),
            )
        ) {
            addPid = "";
            refresh();
        }
    }
    async function removePractitioner(p: S["DashboardPractitionerResponse"]) {
        if (!hasReason()) return;
        if (!confirm(`Remove ${p.displayName} from this session?`)) return;
        if (
            await act(() =>
                ok(
                    api.DELETE("/api/sessions/{sessionId}/practitioners/{practitionerId}", {
                        params: { path: { sessionId: id, practitionerId: p.practitionerId } },
                        body: { reason: r() },
                    }),
                ),
            )
        )
            refresh();
    }
    async function editRemarks(p: S["DashboardPractitionerResponse"]) {
        if (!hasReason()) return;
        const v = prompt(`Remarks for ${p.displayName}`, p.remarks ?? "");
        if (v === null) return;
        if (
            await act(() =>
                ok(
                    api.PATCH("/api/sessions/{sessionId}/practitioners/{practitionerId}", {
                        params: { path: { sessionId: id, practitionerId: p.practitionerId } },
                        body: { remarks: v.trim() || null, reason: r() },
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
        if (!hasReason()) return;
        if (await act(() => ok(api.POST("/api/sessions/{sessionId}/concerns", { params: { path: { sessionId: id } }, body: { concernId, reason: r() } })))) {
            cText = "";
            refresh();
        }
    }
    async function addNewConcern() {
        if (!hasReason()) return;
        const name = cTyped;
        if (!name) return;
        if (
            await act(
                () =>
                    ok(
                        api.POST("/api/sessions/{sessionId}/promote-concern", {
                            params: { path: { sessionId: id } },
                            body: { id: uuid(), label: name, reason: r(), clearOtherConcerns: false },
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
        if (!hasReason()) return;
        if (
            await act(() =>
                ok(
                    api.DELETE("/api/sessions/{sessionId}/concerns/{concernId}", {
                        params: { path: { sessionId: id, concernId: c.id } },
                        body: { reason: r() },
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
        if (!hasReason()) return;
        const v = prompt("New illness (added to the list for everyone)", s?.otherConcerns ?? "");
        if (!v?.trim()) return;
        if (
            await act(
                () =>
                    ok(
                        api.POST("/api/sessions/{sessionId}/promote-concern", {
                            params: { path: { sessionId: id } },
                            body: { id: uuid(), label: v.trim(), reason: r() },
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
    const sessionConcernIds = $derived(new Set(s?.concerns?.map((c) => c.id) ?? []));
</script>

<PageHead title={client.data ? clientName(client.data) : "Session"} sub={s ? `${label(s.sessionType)} · ${dateTime(s.bookedAt)}` : ""}>
    <button onclick={() => goBack()} title="Back to the previous page">‹ Back</button>
    {#if s && edit && !voided}<button onclick={() => (saleOpen = true)}>Product sale</button>{/if}
    {#if s && can("VOID_SESSION")}
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
                    {#if needReason && edit}
                        <label class="field reason"
                            >Reason for changes (required, this day is closed)<input
                                bind:this={reasonBox}
                                bind:value={reason}
                                placeholder="Why this is being changed"
                            /></label
                        >
                    {/if}
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
                            {#if edit && !voided}<button class="sm" onclick={() => hasReason() && ((price = s.finalPrice), pa.reset(), (priceOpen = true))}
                                    >Change</button
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
                        <div class="faint small">
                            New illnesses join the list for everyone. Tap × to take one off this session.
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
                            <div class="faint small">Removing takes a wrong or duplicate entry off the list. Sessions that already have it keep it.</div>
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
        submitText={voided ? "Restore" : "Void"}
        danger={!voided}
        busy={va.busy}
        error={va.error}
    >
        <div class="muted">{voided ? "The session counts toward sales again." : "A voided session stops counting toward sales. It can be restored later."}</div>
        <label class="field"><span class="req">Reason</span><textarea bind:value={voidReason} required></textarea></label>
    </Dialog>

    <ProductSale bind:open={saleOpen} sessionId={id} onsaved={refresh} />
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
    .reason {
        margin-bottom: 10px;
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
