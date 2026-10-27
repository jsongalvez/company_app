<script lang="ts">
    import { ApiError, api, ok, uuid, type S } from "../api/client";
    import { guessPreview, rememberPrice } from "../lib/prices";
    import { rememberClient } from "../lib/clientdir.svelte";
    import { app } from "../lib/app.svelte";
    import { fetchCached, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { label, peso, operationalDate, shortDay } from "../lib/format";
    import { createBody, emptyDraft } from "../lib/client-draft";
    import { COMMON_CONCERNS, sortConcerns, typedConcerns } from "../lib/concerns";
    import { setBooking, type Booking } from "../lib/bookings";
    import Dialog from "./Dialog.svelte";
    import ClientPicker from "./ClientPicker.svelte";
    import ClientForm, { type ClientDraft } from "./ClientForm.svelte";

    let {
        open = $bindable(false),
        onsaved,
        startClient = null,
        booking = null,
    }: { open?: boolean; onsaved: () => void; startClient?: S["ClientResponse"] | null; booking?: Booking | null } = $props();

    const a = new Action();
    let client = $state<S["ClientResponse"] | null>(null);
    let creating = $state(false);
    let draft = $state<ClientDraft>(emptyDraft());
    let preview = $state<S["SessionPreviewResponse"] | null>(null);
    let previewErr = $state<string | null>(null);
    let price = $state("");
    let walkIn = $state(false);
    let requested = $state("");
    let practitioners = $state<string[]>([]);
    let remarks = $state("");
    let otherConcerns = $state("");
    let concerns = $state<S["ConcernResponse"][]>([]);
    let picked = $state<string[]>([]);
    // Tick boxes for the common ones; the rest of the saved list is suggested while typing.
    const isCommon = (l: string) => COMMON_CONCERNS.some((c) => c.toLowerCase() === l.toLowerCase());
    const common = $derived(concerns.filter((k) => isCommon(k.label)));
    const saved = $derived(concerns.filter((k) => !isCommon(k.label)));
    let nextDate = $state("");
    let staff = $state<{ id: string; name: string }[]>([]);

    // Reset each time the dialog opens; load who can be assigned today.
    $effect(() => {
        if (!open) return;
        client = startClient;
        creating = false;
        draft = emptyDraft();
        preview = null;
        previewErr = null;
        price = "";
        walkIn = false;
        requested = "";
        practitioners = app.me ? [app.me.id] : [];
        remarks = otherConcerns = nextDate = "";
        picked = [];
        const cl = load.concerns();
        fetchCached(cl.key, cl.fn).then(
            (l) => (concerns = sortConcerns(l as S["ConcernResponse"][])),
            () => {},
        );
        a.reset();
        const dayId = app.today?.branchDayId;
        const l = dayId ? load.dayUsers(dayId) : null;
        const m = load.members(app.branchId);
        Promise.all([l ? fetchCached(l.key, l.fn).catch(() => []) : Promise.resolve([]), fetchCached(m.key, m.fn).catch(() => [])]).then(([du, mem]) => {
            const map = new Map<string, string>();
            for (const u of du as S["BranchDayUserResponse"][]) map.set(u.userId, u.displayName);
            for (const u of mem as S["BranchMemberResponse"][]) if (!map.has(u.id)) map.set(u.id, u.displayName);
            staff = [...map].map(([id, name]) => ({ id, name })).sort((x, y) => x.name.localeCompare(y.name));
        });
    });

    // The chosen client's past visits, for a quick look before adding today's.
    let hist = $state<S["ClientHistoryResponse"] | null>(null);
    $effect(() => {
        const c = client;
        hist = null;
        if (!c) return;
        const h = load.clientHistory(c.id);
        fetchCached(h.key, h.fn).then(
            (r) => {
                if (client?.id === c.id) hist = r;
            },
            () => {},
        );
    });
    const pastVisits = $derived((hist?.sessions ?? []).filter((v) => !v.voided && v.status === "COMPLETED"));
    const visitCount = $derived((hist ? pastVisits.length : (client?.sessionCount ?? 0)) + (client?.earlierVisits ?? 0));

    // Base rate preview for the chosen client.
    $effect(() => {
        const c = client;
        if (!c) return;
        preview = null;
        previewErr = null;
        ok(api.GET("/api/branches/{branchId}/session-preview", { params: { path: { branchId: app.branchId }, query: { clientId: c.id } } })).then(
            (p) => {
                if (client?.id !== c.id) return;
                preview = p;
                price = p.basePrice;
                rememberPrice(app.branchId, p);
            },
            (e: Error) => {
                if (client?.id !== c.id) return;
                // No signal: work it out from the visits and the rates this phone last saw.
                const guess = e instanceof ApiError && e.status <= 0 ? guessPreview(app.branchId, c) : null;
                if (guess) {
                    preview = guess;
                    price = guess.basePrice;
                    previewErr = "No signal: price from the rates this phone last saw.";
                } else previewErr = e instanceof ApiError && e.status <= 0 ? "No signal: enter the price." : e.message;
            },
        );
    });

    function startNew(q: string) {
        draft = emptyDraft(q);
        creating = true;
    }

    async function save() {
        await a.run(
            async () => {
                let c = client;
                if (creating) {
                    c = await ok(api.POST("/api/clients", { body: createBody(uuid(), draft) }));
                    rememberClient(c);
                    invalidate("clients:");
                    client = c;
                    creating = false;
                    // Price comes from the preview; stop here so the user sees it first.
                    return;
                }
                if (!c) throw new Error("Choose a client");
                if (!price.trim()) throw new Error("Enter a price");
                const s = await ok(
                    api.POST("/api/sessions", {
                        body: {
                            id: uuid(),
                            clientId: c.id,
                            branchId: app.branchId,
                            isWalkIn: walkIn,
                            requestedPractitionerId: requested || null,
                            finalPrice: price.trim(),
                            remarks: remarks.trim() || null,
                            otherConcerns: null,
                            nextAppointmentDate: nextDate || null,
                            practitioners: practitioners.map((practitionerId) => ({ id: uuid(), practitionerId })),
                        },
                    }),
                );
                // Concerns: ticked ones, then typed ones (a known name is picked, a new one joins the list).
                const ids = new Set(picked);
                const fresh: string[] = [];
                for (const name of typedConcerns(otherConcerns)) {
                    const known = concerns.find((k) => k.label.toLowerCase() === name.toLowerCase());
                    if (known) ids.add(known.id);
                    else fresh.push(name);
                }
                for (const concernId of ids)
                    await ok(api.POST("/api/sessions/{sessionId}/concerns", { params: { path: { sessionId: s.id } }, body: { concernId } }));
                for (const label of fresh)
                    await ok(
                        api.POST("/api/sessions/{sessionId}/promote-concern", {
                            params: { path: { sessionId: s.id } },
                            body: { id: uuid(), label, clearOtherConcerns: false },
                        }),
                    );
                if (fresh.length) invalidate("concerns");
                // A booked visit that turned into this session is marked as came.
                if (booking && booking.clientId === c.id) await setBooking(booking, { status: "CAME" }).catch(() => {});
                // "Next visit" also books it, so it shows under Booked visits.
                if (nextDate)
                    await ok(
                        api.POST("/api/branches/{branchId}/bookings", {
                            params: { path: { branchId: app.branchId } },
                            body: { id: uuid(), clientId: c.id, visitDate: nextDate, visitTime: null, note: null },
                        }),
                    ).catch(() => {});
                invalidate("bookings:");
                invalidate("dash:", "clients:", `client:${c.id}`, `session:${s.id}`);
                open = false;
                onsaved();
            },
            creating ? undefined : "Session added",
        );
    }

    function pick(id: string, on: boolean) {
        picked = on ? [...picked, id] : picked.filter((x) => x !== id);
    }

    function toggle(id: string, on: boolean) {
        practitioners = on ? [...practitioners, id] : practitioners.filter((x) => x !== id);
    }
</script>

<Dialog
    bind:open
    title={creating ? "New client" : "New session"}
    width={620}
    onsubmit={save}
    submitText={creating ? "Create client" : "Add session"}
    busy={a.busy}
    error={a.error}
>
    {#if creating}
        <ClientForm bind:v={draft} />
        <div class="row"><button type="button" class="sm" onclick={() => (creating = false)}>Back to search</button></div>
    {:else}
        <div class="col">
            <span class="field-l">Client</span>
            <ClientPicker bind:value={client} onnew={startNew} />
        </div>
        {#if client}
            <div class="visits">
                <b>{visitCount === 0 ? "First visit" : `${visitCount} visit${visitCount === 1 ? "" : "s"} before`}</b>
                {#if client.earlierVisits}<span class="faint">({client.earlierVisits} before the app)</span>{/if}
                <a href={`#/clients/${client.id}`} target="_blank" rel="noopener">See history</a>
                {#each pastVisits.slice(0, 3) as v (v.id)}
                    <div class="faint small visit">
                        {shortDay(v.date)} · {v.practitioners.join(", ") || "No practitioner"}{v.concerns.length ? ` · ${v.concerns.join(", ")}` : ""}
                    </div>
                {/each}
            </div>
            <div class="form-grid">
                <label class="field">Session type<input value={preview ? label(preview.sessionType) : previewErr ? "" : "…"} disabled /></label>
                <label class="field"
                    ><span class="req">Price</span><input class="num" inputmode="decimal" bind:value={price} required />
                    {#if preview && price !== preview.basePrice}<span class="faint">List price {peso(preview.basePrice)}</span>{/if}
                </label>
                <label class="field">Next visit<input type="date" bind:value={nextDate} min={operationalDate()} /></label>
                <label class="check walk"><input type="checkbox" bind:checked={walkIn} />Walk-in</label>
            </div>
            {#if previewErr}<div class="notice warn">{previewErr}</div>{/if}
            <div class="form-grid">
                <label class="field"
                    >Requested practitioner
                    <select bind:value={requested}>
                        <option value="">None</option>
                        {#each staff as p (p.id)}<option value={p.id}>{p.name}</option>{/each}
                    </select>
                </label>
                <div class="field wide">
                    <span>Practitioners working on this session</span>
                    <div class="row wrap checks">
                        {#each staff as p (p.id)}
                            <label class="check"
                                ><input
                                    type="checkbox"
                                    checked={practitioners.includes(p.id)}
                                    onchange={(e) => toggle(p.id, e.currentTarget.checked)}
                                />{p.name}</label
                            >
                        {:else}<span class="faint">Nobody on duty yet.</span>{/each}
                    </div>
                </div>
                <label class="field wide">Remarks<textarea rows="2" bind:value={remarks}></textarea></label>
                <div class="field wide">
                    <span>Illnesses</span>
                    {#if common.length}
                        <div class="row wrap checks">
                            {#each common as k (k.id)}
                                <label class="check"
                                    ><input
                                        type="checkbox"
                                        checked={picked.includes(k.id)}
                                        onchange={(e) => pick(k.id, e.currentTarget.checked)}
                                    />{k.label}</label
                                >
                            {/each}
                        </div>
                    {/if}
                </div>
                <label class="field wide"
                    >Other illness<input list="saved-concerns" bind:value={otherConcerns} placeholder="Type to find a saved one, or add a new one" /></label
                >
                <datalist id="saved-concerns"
                    >{#each saved as k (k.id)}<option value={k.label}></option>{/each}</datalist
                >
            </div>
        {/if}
    {/if}
</Dialog>

<style>
    .visits {
        border: 1px solid var(--line);
        background: var(--panel);
        padding: 4px 8px;
    }
    .visits a {
        margin-left: 6px;
    }
    .visit {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    .field-l {
        font-size: 12px;
        color: var(--text-2);
    }
    .walk {
        align-self: end;
        height: 26px;
    }
    .checks {
        gap: 4px 14px;
        color: var(--text);
        font-size: 13px;
    }
</style>
