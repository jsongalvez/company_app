<script lang="ts">
    import { api, ok } from "../api/client";
    import { app, signIn, toast } from "../lib/app.svelte";
    import { Action } from "../lib/act.svelte";
    import { dateTime } from "../lib/format";
    import PageHead from "../components/PageHead.svelte";

    let name = $state(app.me?.displayName ?? "");
    let email = $state(app.me?.email ?? "");
    const changed = $derived(name.trim() !== (app.me?.displayName ?? "") || email.trim() !== (app.me?.email ?? ""));
    const pa = new Action();

    async function saveProfile(e: SubmitEvent) {
        e.preventDefault();
        await pa.run(async () => {
            app.me = await ok(api.PATCH("/api/me", { body: { displayName: name.trim(), email: email.trim() } }));
            name = app.me.displayName;
            email = app.me.email ?? "";
        }, "Profile saved");
    }

    let current = $state("");
    let next = $state("");
    let next2 = $state("");
    const pw = new Action();

    async function changePassword(e: SubmitEvent) {
        e.preventDefault();
        const username = app.me?.username ?? "";
        const ok2 = await pw.run(async () => {
            if (next !== next2) throw new Error("New passwords don't match");
            await ok(api.POST("/api/me/password", { body: { currentPassword: current, newPassword: next } }));
            // The change ends every session, this one included: sign straight back in.
            await signIn(username, next);
        });
        if (ok2) {
            current = next = next2 = "";
            toast("Password changed. Other devices are signed out.");
        }
    }
</script>

<PageHead title="My profile" sub={app.me ? `Signed in as ${app.me.username}` : ""} />

<div class="grid">
    <form class="panel" onsubmit={saveProfile}>
        <header><h2>Details</h2></header>
        <div class="body col">
            {#if pa.error}<div class="notice bad">{pa.error}</div>{/if}
            <label class="field"><span class="req">Name</span><input bind:value={name} required maxlength="50" autocomplete="name" /></label>
            <label class="field"
                ><span class="req">Email</span><input
                    type="email"
                    bind:value={email}
                    required
                    maxlength="50"
                    autocomplete="email"
                    autocapitalize="none"
                /></label
            >
            <p class="faint">Password reset codes are sent to this email.</p>
            <label class="field">Username<input value={app.me?.username ?? ""} readonly /></label>
            {#if app.me?.createdAt}<p class="faint">Account since {dateTime(app.me.createdAt)}</p>{/if}
            <div class="row"><button class="primary" type="submit" disabled={pa.busy || !changed}>Save</button></div>
        </div>
    </form>

    <form class="panel" onsubmit={changePassword}>
        <header><h2>Change password</h2></header>
        <div class="body col">
            {#if pw.error}<div class="notice bad">{pw.error}</div>{/if}
            <label class="field">Current password<input type="password" bind:value={current} required autocomplete="current-password" /></label>
            <label class="field">New password<input type="password" bind:value={next} required minlength="8" autocomplete="new-password" /></label>
            <label class="field">Repeat new password<input type="password" bind:value={next2} required minlength="8" autocomplete="new-password" /></label>
            <p class="faint">At least 8 characters. Changing it signs you out on other devices.</p>
            <div class="row"><button class="primary" type="submit" disabled={pw.busy}>Change password</button></div>
        </div>
    </form>
</div>

<style>
    .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 380px));
        gap: 12px;
        align-items: start;
    }
    p {
        margin: 0;
    }
</style>
