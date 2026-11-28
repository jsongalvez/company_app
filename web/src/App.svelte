<script lang="ts">
    import { ApiError } from "./api/client";
    import { app, boot } from "./lib/app.svelte";
    import { route } from "./lib/router.svelte";
    import Login from "./pages/Login.svelte";
    import { loadShell } from "./lib/shell";
    import Toast from "./components/Toast.svelte";
    import ReasonAsk from "./components/ReasonAsk.svelte";

    void boot().catch((e) => {
        if (!(e instanceof ApiError && e.status <= 0)) throw e;
    });

    // Token-carrying links from email work while signed out.
    const publicPage = $derived(["reset-password", "accept-invite", "forgot-password"].includes(route.current.parts[0] ?? ""));
</script>

{#if !app.ready}
    <div class="boot faint">Loading…</div>
{:else if !app.signedIn || publicPage}
    <Login />
{:else}
    {#await loadShell() then shell}
        <shell.default />
    {:catch}
        <div class="boot">Couldn't open the app. <button onclick={() => location.reload()}>Reload</button></div>
    {/await}
{/if}
<Toast />
<ReasonAsk />

<style>
    .boot {
        padding: 20px;
    }
</style>
