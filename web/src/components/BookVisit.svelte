<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { rememberClient } from "../lib/clientdir.svelte";
    import { app, branchName } from "../lib/app.svelte";
    import { invalidate } from "../lib/cache.svelte";
    import { Action } from "../lib/act.svelte";
    import { clientName, operationalDate, addDays } from "../lib/format";
    import { createBody, emptyDraft } from "../lib/client-draft";
    import { setBooking, type Booking } from "../lib/bookings";
    import Dialog from "./Dialog.svelte";
    import ClientPicker from "./ClientPicker.svelte";
    import ClientForm, { type ClientDraft } from "./ClientForm.svelte";

    /** Book a client for a later day, or (with `booking`) move or re-note one already booked. */
    let {
        open = $bindable(false),
        client: preset = null,
        booking = null,
        onsaved,
    }: { open?: boolean; client?: S["ClientResponse"] | null; booking?: Booking | null; onsaved?: () => void } = $props();

    const a = new Action();
    let client = $state<S["ClientResponse"] | null>(null);
    let creating = $state(false);
    let draft = $state<ClientDraft>(emptyDraft());
    let date = $state("");
    let at = $state("");
    let note = $state("");

    $effect(() => {
        if (!open) return;
        client = preset;
        creating = false;
        draft = emptyDraft();
        date = booking?.visitDate ?? addDays(operationalDate(), 7);
        at = booking?.visitTime ?? "";
        note = booking?.note ?? "";
        a.reset();
    });

    async function save() {
        await a.run(
            async () => {
                if (creating) {
                    const c = await ok(api.POST("/api/clients", { body: createBody(uuid(), draft) }));
                    rememberClient(c);
                    invalidate("clients:");
                    client = c;
                    creating = false;
                    return;
                }
                if (!date) throw new Error("Pick a day");
                if (booking) {
                    await setBooking(booking, { visitDate: date, visitTime: at || null, note: note.trim() || null });
                } else {
                    if (!client) throw new Error("Choose a client");
                    await ok(
                        api.POST("/api/branches/{branchId}/bookings", {
                            params: { path: { branchId: app.branchId } },
                            body: { id: uuid(), clientId: client.id, visitDate: date, visitTime: at || null, note: note.trim() || null },
                        }),
                    );
                    invalidate("bookings:");
                }
                open = false;
                onsaved?.();
            },
            creating ? undefined : booking ? "Visit changed" : "Visit booked",
        );
    }
</script>

<Dialog
    bind:open
    title={creating ? "New client" : booking ? "Change booked visit" : "Book a visit"}
    width={520}
    onsubmit={save}
    submitText={creating ? "Create client" : booking ? "Save" : "Book visit"}
    busy={a.busy}
    error={a.error}
>
    {#if creating}
        <ClientForm bind:v={draft} />
        <div class="row"><button type="button" class="sm" onclick={() => (creating = false)}>Back to search</button></div>
    {:else}
        <div class="col">
            <span class="field-l">Client</span>
            {#if booking}
                <b>{booking.clientName}</b>
            {:else if preset}
                <b>{clientName(preset)}</b>
            {:else}
                <ClientPicker
                    bind:value={client}
                    onnew={(q) => {
                        draft = emptyDraft(q);
                        creating = true;
                    }}
                />
            {/if}
        </div>
        <div class="form-grid">
            <label class="field"><span class="req">Day</span><input type="date" bind:value={date} min={operationalDate()} required /></label>
            <label class="field">Time (optional)<input type="time" bind:value={at} /></label>
            <label class="field wide">Note<input bind:value={note} maxlength="500" placeholder="e.g. follow-up for back pain" /></label>
        </div>
        <p class="faint small">At {branchName(booking?.branchId ?? app.branchId)}. Nothing is charged until the client comes in.</p>
    {/if}
</Dialog>

<style>
    .field-l {
        font-size: 12px;
        color: var(--text-2);
    }
</style>
