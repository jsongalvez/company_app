<script lang="ts">
    import { api, ok, errText } from "../api/client";
    import { app, signIn } from "../lib/app.svelte";
    import { route, go } from "../lib/router.svelte";

    const mode = $derived(route.current.parts[0] ?? "");
    let code = $state(route.current.query.get("code") ?? "");

    let username = $state("");
    let password = $state("");
    let password2 = $state("");
    let identifier = $state("");
    let busy = $state(false);
    let error = $state<string | null>(null);
    let info = $state<string | null>(null);

    async function submit(e: SubmitEvent) {
        e.preventDefault();
        error = null;
        info = null;
        busy = true;
        try {
            if (mode === "forgot-password") {
                await ok(api.POST("/auth/forgot-password", { body: { identifier } }));
                info = "If that account exists, a reset code was emailed. Enter it below.";
                go("/reset-password");
            } else if (mode === "reset-password" || mode === "accept-invite") {
                if (password !== password2) throw new Error("Passwords don't match");
                const body = { token: code.trim(), newPassword: password };
                if (mode === "reset-password") await ok(api.POST("/auth/reset-password", { body }));
                else await ok(api.POST("/auth/accept-invite", { body }));
                info = "Password set. Sign in with it now.";
                password = password2 = "";
                go("/");
            } else {
                await signIn(username.trim(), password);
                go("/");
            }
        } catch (err) {
            error = errText(err);
        } finally {
            busy = false;
        }
    }

    const title = $derived(
        mode === "forgot-password"
            ? "Reset password"
            : mode === "reset-password"
              ? "Choose a new password"
              : mode === "accept-invite"
                ? "Set your password"
                : "Sign in",
    );
</script>

<main>
    <form class="panel" onsubmit={submit}>
        <header>
            <h1>CompanyApp</h1>
            <span class="spacer"></span><span class="muted">{title}</span>
        </header>
        <div class="body col">
            {#if error}<div class="notice bad">{error}</div>{/if}
            {#if info}<div class="notice ok">{info}</div>{/if}

            {#if mode === "forgot-password"}
                <label class="field">Username or email<input bind:value={identifier} required autocomplete="username" /></label>
            {:else if mode === "reset-password" || mode === "accept-invite"}
                <label class="field"
                    >{mode === "accept-invite" ? "Invite code" : "Reset code from email"}<input
                        bind:value={code}
                        required
                        autocomplete="one-time-code"
                        autocapitalize="none"
                    /></label
                >
                <label class="field">New password<input type="password" bind:value={password} required minlength="8" autocomplete="new-password" /></label>
                <label class="field">Repeat password<input type="password" bind:value={password2} required autocomplete="new-password" /></label>
            {:else}
                <label class="field">Username<input bind:value={username} required autocomplete="username" autocapitalize="none" /></label>
                <label class="field">Password<input type="password" bind:value={password} required autocomplete="current-password" /></label>
            {/if}

            <div class="row">
                <button class="primary" type="submit" disabled={busy}>
                    {mode === "forgot-password" ? "Send reset link" : mode === "" || mode === "login" ? "Sign in" : "Set password"}
                </button>
                <span class="spacer"></span>
                {#if mode === "forgot-password" || mode === "reset-password" || mode === "accept-invite"}
                    <a href="#/">{app.signedIn ? "Back to app" : "Back to sign in"}</a>
                {:else}
                    <a href="#/forgot-password">Forgot password?</a>
                    <a href="#/accept-invite">Have an invite code?</a>
                {/if}
            </div>
        </div>
    </form>
</main>

<style>
    main {
        min-height: 100%;
        display: grid;
        place-items: start center;
        padding: 12vh 12px 12px;
    }
    form {
        width: 100%;
        max-width: 340px;
    }
</style>
