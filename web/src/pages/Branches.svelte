<script lang="ts">
    import { api, ok, uuid, type S } from "../api/client";
    import { can, canAtBranch, canAnywhere, refreshBranchNames } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action, act } from "../lib/act.svelte";
    import { go, route } from "../lib/router.svelte";
    import { label, peso, day, dateTime } from "../lib/format";
    import { SESSION_TYPES } from "../lib/rules";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";
    import PersonPicker from "../components/PersonPicker.svelte";
    import PeopleChecklist from "../components/PeopleChecklist.svelte";

    const list = new Query(load.branches().key, load.branches().fn);
    const sel = $derived(route.current.query.get("id") ?? "");
    const branch = $derived(list.data?.find((b) => b.id === sel));
    $effect(() => {
        if (!sel && list.data?.length) go("/branches", { id: list.data[0]!.id });
    });

    const manageUsers = $derived(canAnywhere("MANAGE_USERS"));
    const manageHelpers = $derived(canAnywhere("ASSIGN_DELEGATE"));
    // 503bf228: any branch member sees its session prices; anyone who can edit the branch changes them. Product prices and commission live on Stock.
    const seeRates = $derived(!!sel && canAtBranch("VIEW_BRANCH_DATA", sel));
    const manageRates = $derived(!!sel && canAtBranch("EDIT_BRANCH_DATA", sel));

    let users = $state<S["UserSummaryResponse"][]>([]);
    $effect(() => {
        if (!manageUsers && !manageHelpers) return;
        const l = load.users();
        fetchCached(l.key, l.fn)
            .then((u) => (users = u))
            .catch(() => {});
    });
    const nameOf = $derived(Object.fromEntries(users.map((u) => [u.id, u.displayName])));

    const staff = new Query<S["AssignmentResponse"][]>();
    const rates = new Query<S["RateResponse"][]>();
    const helpers = new Query<S["DelegateResponse"][]>();
    $effect(() => {
        if (!sel) return;
        const a = load.assignments(sel);
        staff.load(a.key, a.fn);
        // Prices are readable by the branch's own staff.
        if (seeRates) {
            const r = load.rates(sel);
            rates.load(r.key, r.fn);
        }
        if (branch?.branchType === "MEDICAL_MISSION") {
            const d = load.delegates(sel);
            helpers.load(d.key, d.fn);
        }
    });
    const activeStaff = $derived((staff.data ?? []).filter((a) => !a.endedAt).sort((a, b) => a.slot - b.slot));
    const activeHelpers = $derived((helpers.data ?? []).filter((d) => !d.endedAt));
    const currentRates = $derived(
        SESSION_TYPES.filter((t) => (branch?.branchType === "MEDICAL_MISSION") === (t === "MEDICAL_MISSION")).map((t) => ({
            type: t,
            rate: (rates.data ?? []).filter((r) => r.sessionType === t).sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0],
        })),
    );

    function refresh(...keys: string[]) {
        invalidate(...keys);
        staff.refresh();
        if (seeRates) rates.refresh();
        if (branch?.branchType === "MEDICAL_MISSION") helpers.refresh();
    }

    // New branch
    const ba = new Action();
    let bOpen = $state(false);
    let bName = $state("");
    let bType = $state<"CLINIC" | "PROVINCIAL_TOUR" | "MEDICAL_MISSION">("CLINIC");
    async function createBranch() {
        const id = uuid();
        if (await ba.run(() => ok(api.POST("/api/branches", { body: { id, name: bName.trim(), branchType: bType } })), "Branch created")) {
            bOpen = false;
            invalidate("branches");
            list.refresh();
            go("/branches", { id });
        }
    }

    // Rename
    const ra = new Action();
    let rOpen = $state(false);
    let rName = $state("");
    function openRename() {
        ra.reset();
        rName = branch?.name ?? "";
        rOpen = true;
    }
    async function renameBranch() {
        if (!branch) return;
        const branchId = branch.id;
        if (await ra.run(() => ok(api.PATCH("/api/branches/{branchId}", { params: { path: { branchId } }, body: { name: rName.trim() } })), "Branch renamed")) {
            rOpen = false;
            invalidate("branches");
            list.refresh();
            void refreshBranchNames();
        }
    }

    // Staff
    const sa = new Action();
    let sOpen = $state(false);
    let sPicked = $state<string[]>([]);
    function openStaff() {
        sa.reset();
        sPicked = [];
        sOpen = true;
    }
    // Slots go on after the last one, in tap order. A failure stops the rest; those already added stay.
    async function addStaff() {
        const first = (activeStaff.at(-1)?.slot ?? 0) + 1;
        const added = new Set<string>();
        const done = await sa.run(
            async () => {
                for (const [i, userId] of sPicked.entries()) {
                    await ok(
                        api.POST("/api/branches/{branchId}/assignments", {
                            params: { path: { branchId: sel } },
                            body: { id: uuid(), userId, slot: first + i },
                        }),
                    );
                    added.add(userId);
                }
            },
            sPicked.length === 1 ? "Added" : `Added ${sPicked.length} people`,
        );
        if (added.size) refresh(`assign:${sel}`, "users");
        if (done) sOpen = false;
        else sPicked = sPicked.filter((id) => !added.has(id));
    }
    async function removeStaff(a: S["AssignmentResponse"]) {
        if (!confirm(`Remove ${nameOf[a.userId] ?? "this person"} from ${branch?.name}? Their past records stay.`)) return;
        if (
            await act(
                () => ok(api.DELETE("/api/branches/{branchId}/assignments/{assignmentId}", { params: { path: { branchId: sel, assignmentId: a.id } } })),
                "Removed",
            )
        )
            refresh(`assign:${sel}`, "users");
    }
    async function move(i: number, dir: -1 | 1) {
        const a = activeStaff[i];
        const b = activeStaff[i + dir];
        if (!a || !b) return;
        if (
            await act(() =>
                ok(
                    api.POST("/api/branches/{branchId}/slots/swap", {
                        params: { path: { branchId: sel } },
                        body: { assignmentIdA: a.id, assignmentIdB: b.id },
                    }),
                ),
            )
        )
            refresh(`assign:${sel}`);
    }
    const unassigned = $derived(users.filter((u) => u.status !== "INACTIVE" && !activeStaff.some((a) => a.userId === u.id)));

    // Price list
    const pa = new Action();
    let pOpen = $state(false);
    let pType = $state<(typeof SESSION_TYPES)[number]>("REGULAR");
    let pRate = $state("");
    function openRate(t: (typeof SESSION_TYPES)[number], current?: string) {
        pa.reset();
        pType = t;
        pRate = current ?? "";
        pOpen = true;
    }
    async function saveRate() {
        const body = { id: uuid(), sessionType: pType, rate: pRate.trim() };
        if (await pa.run(() => ok(api.POST("/api/branches/{branchId}/rates", { params: { path: { branchId: sel } }, body })), "Price saved")) {
            pOpen = false;
            refresh(`rates:${sel}`);
        }
    }

    // Mission helpers
    const ha = new Action();
    let hOpen = $state(false);
    let hUser = $state("");
    async function addHelper() {
        const body = { delegateId: uuid(), targetUserId: hUser, branchId: sel };
        if (await ha.run(() => ok(api.POST("/api/delegates", { body })), "Helper added")) {
            hOpen = false;
            refresh(`deleg:${sel}`);
        }
    }
    async function removeHelper(d: S["DelegateResponse"]) {
        if (!confirm(`Remove ${nameOf[d.targetUser] ?? "this helper"}?`)) return;
        if (await act(() => ok(api.DELETE("/api/delegates/{delegateId}", { params: { path: { delegateId: d.id } } })), "Helper removed"))
            refresh(`deleg:${sel}`);
    }
</script>

<PageHead title="Branches">
    {#if manageUsers}<button class="primary" onclick={() => (ba.reset(), (bName = ""), (bOpen = true))}>New branch</button>{/if}
</PageHead>

<div class="layout">
    <aside class="panel list">
        <State q={list} empty={!!list.data && list.data.length === 0} emptyText="No branches yet." />
        <table class="grid">
            <tbody>
                {#each list.data ?? [] as b (b.id)}
                    <tr class="click" class:sel={b.id === sel} onclick={() => go("/branches", { id: b.id })}>
                        <td>{b.name}</td>
                        <td class="muted right">{label(b.branchType)}</td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </aside>

    {#if branch}
        <div class="col grow main">
            <section class="panel">
                <header>
                    <h2>Staff at {branch.name}</h2>
                    <span class="spacer"></span>
                    {#if manageUsers}<button class="sm" onclick={openRename}>Rename branch</button><button class="sm" onclick={openStaff}>Add person</button
                        >{/if}
                </header>
                <State q={staff} empty={!!staff.data && activeStaff.length === 0} emptyText="No one works here yet." />
                {#if activeStaff.length}
                    <table class="grid">
                        <thead><tr><th class="num">Order</th><th>Name</th><th class="hide-sm">Since</th><th></th></tr></thead>
                        <tbody>
                            {#each activeStaff as a, i (a.id)}
                                <tr>
                                    <td class="num faint">{i + 1}</td>
                                    <td>{nameOf[a.userId] ?? "…"}</td>
                                    <td class="muted hide-sm">{dateTime(a.assignedAt)}</td>
                                    <td class="right nowrap">
                                        {#if manageUsers}
                                            <button class="sm" onclick={() => move(i, -1)} disabled={i === 0} title="Move up" aria-label="Move up">↑</button>
                                            <button
                                                class="sm"
                                                onclick={() => move(i, 1)}
                                                disabled={i === activeStaff.length - 1}
                                                title="Move down"
                                                aria-label="Move down">↓</button
                                            >
                                            <button class="sm" onclick={() => removeStaff(a)}>Remove</button>
                                        {/if}
                                    </td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                {/if}
            </section>

            {#if seeRates}
                <section class="panel">
                    <header><h2>Price list</h2></header>
                    <State q={rates} />
                    <table class="grid">
                        <tbody>
                            {#each currentRates as c (c.type)}
                                <tr>
                                    <td>{label(c.type)}</td>
                                    <td class="num">{c.rate ? peso(c.rate.rate) : "Not set"}</td>
                                    <td class="muted hide-sm">{c.rate ? `since ${day(c.rate.effectiveFrom.slice(0, 10))}` : ""}</td>
                                    <td class="right">
                                        {#if manageRates && c.type !== "MEDICAL_MISSION"}<button class="sm" onclick={() => openRate(c.type, c.rate?.rate)}
                                                >Change</button
                                            >{/if}
                                    </td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                </section>
            {/if}

            {#if branch.branchType === "MEDICAL_MISSION"}
                <section class="panel">
                    <header>
                        <h2>Mission helpers</h2>
                        <span class="spacer"></span>
                        {#if manageHelpers}<button class="sm" onclick={() => (ha.reset(), (hUser = ""), (hOpen = true))}>Add helper</button>{/if}
                    </header>
                    <State q={helpers} empty={!!helpers.data && activeHelpers.length === 0} emptyText="No helpers." />
                    {#if activeHelpers.length}
                        <table class="grid">
                            <tbody>
                                {#each activeHelpers as d (d.id)}
                                    <tr>
                                        <td>{nameOf[d.targetUser] ?? "…"}</td>
                                        <td class="muted">{dateTime(d.assignedAt)}</td>
                                        <td class="right"
                                            >{#if manageHelpers}<button class="sm" onclick={() => removeHelper(d)}>Remove</button>{/if}</td
                                        >
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    {/if}
                </section>
            {/if}
        </div>
    {/if}
</div>

<Dialog bind:open={bOpen} title="New branch" width={400} onsubmit={createBranch} submitText="Create" busy={ba.busy || !bName.trim()} error={ba.error}>
    <label class="field"><span class="req">Name</span><input bind:value={bName} required /></label>
    <label class="field"
        >Kind
        <select bind:value={bType}>
            <option value="CLINIC">Clinic</option>
            <option value="PROVINCIAL_TOUR">Provincial tour</option>
            <option value="MEDICAL_MISSION">Medical mission</option>
        </select>
    </label>
</Dialog>

<Dialog
    bind:open={rOpen}
    title="Rename {branch?.name}"
    width={400}
    onsubmit={renameBranch}
    submitText="Rename"
    busy={ra.busy || !rName.trim()}
    error={ra.error}
>
    <label class="field"><span class="req">New name</span><input bind:value={rName} maxlength="100" required /></label>
</Dialog>

<Dialog
    bind:open={sOpen}
    title="Add person to {branch?.name}"
    width={400}
    onsubmit={addStaff}
    submitText={sPicked.length ? `Add ${sPicked.length}` : "Add"}
    busy={sa.busy || !sPicked.length}
    error={sa.error}
>
    <div class="field"><span class="req">People</span><PeopleChecklist people={unassigned} bind:value={sPicked} /></div>
</Dialog>

<Dialog bind:open={pOpen} title="Price: {label(pType)}" width={340} onsubmit={saveRate} busy={pa.busy} error={pa.error}>
    <label class="field"
        ><span class="req">Price</span><input class="num" inputmode="decimal" bind:value={pRate} required pattern={"\\d+(\\.\\d{1,2})?"} /></label
    >
</Dialog>

<Dialog bind:open={hOpen} title="Add mission helper" width={400} onsubmit={addHelper} submitText="Add" busy={ha.busy || !hUser} error={ha.error}>
    <div class="field">
        <span class="req">Person</span><PersonPicker
            people={users.filter((u) => u.status !== "INACTIVE" && !activeHelpers.some((d) => d.targetUser === u.id))}
            bind:value={hUser}
        />
    </div>
</Dialog>

<style>
    .layout {
        display: flex;
        gap: 12px;
        align-items: flex-start;
    }
    .list {
        width: 260px;
        flex: none;
    }
    .main {
        gap: 12px;
        min-width: 0;
    }
    @media (max-width: 760px) {
        .layout {
            flex-direction: column;
            align-items: stretch;
        }
        .list {
            width: auto;
        }
    }
</style>
