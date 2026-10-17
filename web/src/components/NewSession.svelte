<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { app } from "../lib/app.svelte";
    import { fetchCached, invalidate } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { label, peso, operationalDate } from "../lib/format";
    import { createBody, emptyDraft } from "../lib/client-draft";
    import Dialog from "./Dialog.svelte";
    import ClientPicker from "./ClientPicker.svelte";
    import ClientForm, { type ClientDraft } from "./ClientForm.svelte";

    let { open = $bindable(false), onsaved }: { open?: boolean; onsaved: () => void } = $props();

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
    let nextDate = $state("");
    let staff = $state<{ id: string; name: string }[]>([]);

    // Reset each time the dialog opens; load who can be assigned today.
    $effect(() => {
        if (!open) return;
        client = null;
        creating = false;
        draft = emptyDraft();
        preview = null;
        previewErr = null;
        price = "";
        walkIn = false;
        requested = "";
        practitioners = app.me ? [app.me.id] : [];
        remarks = otherConcerns = nextDate = "";
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
            },
            (e: Error) => (previewErr = e.message),
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
                            otherConcerns: otherConcerns.trim() || null,
                            nextAppointmentDate: nextDate || null,
                        },
                    }),
                );
                for (const pid of practitioners) {
                    await ok(
                        api.POST("/api/sessions/{sessionId}/practitioners", {
                            params: { path: { sessionId: s.id } },
                            body: { id: uuid(), practitionerId: pid },
                        }),
                    );
                }
                invalidate("dash:", "clients:", `client:${c.id}`);
                open = false;
                onsaved();
            },
            creating ? undefined : "Session added",
        );
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
                <label class="field wide"
                    >Other concerns<input bind:value={otherConcerns} placeholder="Notes. Pick listed concerns on the session page after saving" /></label
                >
            </div>
        {/if}
    {/if}
</Dialog>

<style>
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
