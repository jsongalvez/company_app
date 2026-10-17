<script lang="ts">
    import { api, ok, type S } from "../api/client";
    import { app } from "../lib/app.svelte";
    import { Query, invalidate, fetchCached } from "../lib/cache.svelte";
    import { load } from "../lib/data";
    import { Action, act } from "../lib/act.svelte";
    import { label, dateTime } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";
    import State from "../components/State.svelte";
    import Dialog from "../components/Dialog.svelte";

    type U = S["UserSummaryResponse"];
    const q = new Query(load.users().key, load.users().fn);
    let roles = $state<string[]>([]);
    $effect(() => {
        const l = load.roles();
        fetchCached(l.key, l.fn)
            .then((r) => (roles = r.map((x) => x.name)))
            .catch(() => {});
    });

    let filter = $state("");
    let showInactive = $state(false);
    const rows = $derived(
        (q.data ?? [])
            .filter((u) => showInactive || u.status !== "INACTIVE")
            .filter((u) => !filter || `${u.displayName} ${u.username}`.toLowerCase().includes(filter.toLowerCase()))
            .sort((a, b) => a.displayName.localeCompare(b.displayName)),
    );
    const inactiveCount = $derived((q.data ?? []).filter((u) => u.status === "INACTIVE").length);

    function refresh() {
        invalidate("users", "assign:");
        q.refresh();
    }

    // Roles
    const ra = new Action();
    let rOpen = $state(false);
    let who = $state<U | null>(null);
    let picked = $state<string[]>([]);
    function openRoles(u: U) {
        ra.reset();
        who = u;
        picked = [...(u.roles ?? [])].filter((r) => r !== "ONBOARDING");
        rOpen = true;
    }
    async function saveRoles() {
        const u = who;
        if (!u) return;
        const body = { roles: picked.length ? picked : ["ONBOARDING"] };
        if (await ra.run(() => ok(api.PUT("/api/users/{userId}/roles", { params: { path: { userId: u.id } }, body })), "Roles saved")) {
            rOpen = false;
            refresh();
        }
    }

    async function setActive(u: U, on: boolean) {
        if (!on && !confirm(`Deactivate ${u.displayName}? They are signed out at once and can't sign in. Nothing they did is deleted.`)) return;
        const path = on ? "/api/users/{userId}/reactivate" : "/api/users/{userId}/deactivate";
        if (await act(() => ok(api.PATCH(path, { params: { path: { userId: u.id } } })), on ? "Reactivated" : "Deactivated")) refresh();
    }

    // Invite
    const ia = new Action();
    let iOpen = $state(false);
    let iName = $state("");
    let iUser = $state("");
    let iEmail = $state("");
    let iRoles = $state<string[]>([]);
    let minted = $state<S["InviteMintResponse"] | null>(null);
    function openInvite() {
        ia.reset();
        iName = iUser = iEmail = "";
        iRoles = [];
        minted = null;
        iOpen = true;
    }
    async function invite() {
        await ia.run(async () => {
            minted = await ok(
                api.POST("/api/invites", {
                    body: { displayName: iName.trim(), username: iUser.trim(), email: iEmail.trim(), roles: iRoles.length ? iRoles : undefined },
                }),
            );
            refresh();
        });
    }
    async function copy(text: string) {
        try {
            await navigator.clipboard.writeText(text);
        } catch {
            /* not allowed: the code is on screen */
        }
    }
    const assignable = $derived(roles.filter((r) => r !== "ONBOARDING" && r !== "SUPERUSER"));
</script>

<PageHead title="Users" sub="{rows.length} shown">
    <input type="search" placeholder="Filter by name" bind:value={filter} />
    {#if inactiveCount}<label class="check"><input type="checkbox" bind:checked={showInactive} />Show deactivated ({inactiveCount})</label>{/if}
    <button class="primary" onclick={openInvite}>Invite user</button>
</PageHead>

<State {q} empty={!!q.data && rows.length === 0} emptyText="No users match." />
{#if rows.length}
    <div class="table-wrap">
        <table class="grid stack">
            <thead><tr><th>Name</th><th>Username</th><th>Roles</th><th>Home branches</th><th>Status</th><th></th></tr></thead>
            <tbody>
                {#each rows as u (u.id)}
                    <tr class:dim={u.status === "INACTIVE"}>
                        <td class="lead"
                            >{u.displayName}{#if u.id === app.me?.id}<span class="faint">&nbsp;(you)</span>{/if}</td
                        >
                        <td class="muted hide-sm">{u.username}</td>
                        <td>{(u.roles ?? []).map(label).join(", ")}</td>
                        <td class="muted full">{(u.assignments ?? []).map((a) => a.branchName).join(", ")}</td>
                        <td>
                            {#if u.status === "INACTIVE"}<span class="mark plain" title={dateTime(u.deactivatedAt)}>Deactivated</span>{/if}
                            {#if !u.roles?.length || u.roles.includes("ONBOARDING")}<span class="mark warn">No access yet</span>{/if}
                        </td>
                        <td class="right nowrap">
                            {#if u.id !== app.me?.id}
                                {#if u.status !== "INACTIVE"}
                                    <button class="sm" onclick={() => openRoles(u)}>Roles</button>
                                    <button class="sm" onclick={() => setActive(u, false)}>Deactivate</button>
                                {:else}
                                    <button class="sm" onclick={() => setActive(u, true)}>Reactivate</button>
                                {/if}
                            {/if}
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
    <div class="faint note">Home branches are set on the <a href="#/branches">Branches</a> page.</div>
{/if}

<Dialog bind:open={rOpen} title="Roles: {who?.displayName}" width={380} onsubmit={saveRoles} busy={ra.busy} error={ra.error}>
    <div class="muted">Roles decide what someone can do. With none, they can sign in but see nothing.</div>
    <div class="col">
        {#each assignable as r (r)}
            <label class="check"><input type="checkbox" value={r} bind:group={picked} />{label(r)}</label>
        {/each}
    </div>
</Dialog>

<Dialog
    bind:open={iOpen}
    title="Invite user"
    width={440}
    onsubmit={minted ? undefined : invite}
    submitText="Create invite"
    cancelText={minted ? "Done" : "Cancel"}
    busy={ia.busy}
    error={ia.error}
>
    {#if minted}
        <div>Give {iName} this invite code. They use it on the sign-in page under "Have an invite code?".</div>
        <div class="code row">
            <input class="mono grow" readonly value={minted.inviteCode} aria-label="Invite code" onfocus={(e) => e.currentTarget.select()} />
            <button type="button" class="sm" onclick={() => copy(minted?.inviteCode ?? "")}>Copy</button>
        </div>
        <div class="muted">Works until {dateTime(minted.expiresAt)}.</div>
    {:else}
        <div class="form-grid">
            <label class="field wide"><span class="req">Full name</span><input bind:value={iName} required /></label>
            <label class="field"><span class="req">Username</span><input bind:value={iUser} required autocomplete="off" /></label>
            <label class="field"><span class="req">Email</span><input type="email" bind:value={iEmail} required /></label>
        </div>
        <div class="muted">Roles (you can change these later)</div>
        <div class="row wrap">
            {#each assignable as r (r)}<label class="check"><input type="checkbox" value={r} bind:group={iRoles} />{label(r)}</label>{/each}
        </div>
    {/if}
</Dialog>

<style>
    .note {
        padding: 8px 0;
    }
    .code {
        border: 1px solid var(--line-strong);
        background: var(--panel-2);
        padding: 8px 10px;
        font-size: 16px;
    }
</style>
