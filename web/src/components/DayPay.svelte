<script lang="ts">
    import { keepReason, lastReason } from "../lib/ask.svelte";
    import { api, ok, uuid, type S } from "../api/client";
    import { can } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action } from "../lib/act.svelte";
    import { peso, sum } from "../lib/format";
    import State from "./State.svelte";
    import Dialog from "./Dialog.svelte";

    /** One branch day's pay and allowances, for whoever pays there. Commission is never here: it stays with the branch (owed #26).
     *  `onpay` runs after pay changes: the day's totals count pay, not allowances. */
    let { branchId, dayId, status, onpay }: { branchId: string; dayId: string; status: S["RemittanceDayPickerEntryResponse"]["status"]; onpay?: () => void } =
        $props();

    const comp = new Query<S["CompensationResponse"][]>();
    const allow = new Query<S["AllowanceResponse"][]>();
    $effect(() => {
        const c = load.compensations(dayId);
        const a = load.allowances(dayId);
        comp.load(c.key, c.fn);
        allow.load(a.key, a.fn);
    });

    // Who can be paid: whoever worked the day, then the branch's members.
    let people = $state<{ id: string; name: string }[]>([]);
    const nameOf = $derived(Object.fromEntries(people.map((p) => [p.id, p.name])));
    $effect(() => {
        const du = load.dayUsers(dayId);
        const mem = load.members(branchId);
        Promise.all([fetchCached(du.key, du.fn).catch(() => []), fetchCached(mem.key, mem.fn).catch(() => [])]).then(([a, b]) => {
            const m = new Map<string, string>();
            for (const u of a as S["BranchDayUserResponse"][]) m.set(u.userId, u.displayName);
            for (const u of b as S["BranchMemberResponse"][]) if (!m.has(u.id)) m.set(u.id, u.displayName);
            people = [...m].map(([id, name]) => ({ id, name })).sort((x, y) => x.name.localeCompare(y.name));
        });
    });

    const closed = $derived(status !== "OPEN");
    // The user (owed #25): pay on earlier days needs a reason, not past-day rights; a remitted day still does.
    const payEdit = $derived(can("ASSIGN_COMPENSATION", branchId) && (status !== "REMITTED" || can("EDIT_PAST_DAY", branchId)));

    let reason = $state("");
    function r() {
        keepReason(reason);
        return reason.trim() || null;
    }
    const startReason = () => (reason = closed ? lastReason() : "");
    function refresh(list: "comp" | "allow") {
        invalidate(`${list}:${dayId}`);
        if (list === "allow") return allow.refresh();
        comp.refresh();
        onpay?.();
    }

    // ---- Pay dialog ----
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
            refresh("comp");
        }
    }

    // ---- Allowance dialog ----
    const aa = new Action();
    let aOpen = $state(false);
    let aUser = $state("");
    let aAmount = $state("");
    function openAllowance() {
        aUser = "";
        aAmount = "";
        startReason();
        aa.reset();
        aOpen = true;
    }
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
</script>

<section class="panel" aria-label="Pay">
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
                            >{c.userName ?? nameOf[c.userId] ?? ""}{#if c.workBranchDayId !== c.payingBranchDayId}<span class="mark plain">Relief</span
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

<section class="panel" aria-label="Allowances">
    <header>
        <h2>Allowances</h2>
        <span class="faint">{peso(sum((allow.data ?? []).map((a) => a.amount)))}</span>
        <span class="spacer"></span>
        {#if payEdit}<button class="sm primary" onclick={openAllowance}>Add</button>{/if}
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
