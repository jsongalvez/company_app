<script lang="ts">
    import { screen, install } from "../lib/screen.svelte";
    import Dialog from "./Dialog.svelte";
    /** As a bar on iPhone and iPad: Safari may clear a site's data after a week unused unless it is on the Home Screen. */
    let { nudge = false }: { nudge?: boolean } = $props();
    let howOpen = $state(false);

    const LATER_KEY = "installLater";
    const LATER_MS = 3 * 86_400_000;
    let later = $state(Number(localStorage.getItem(LATER_KEY) ?? 0) > Date.now());
    function notNow() {
        localStorage.setItem(LATER_KEY, String(Date.now() + LATER_MS));
        later = true;
    }
</script>

{#if nudge}
    {#if screen.iosHint && !screen.installed && !later}
        <div class="nudge" role="status">
            <span>Add to Home Screen</span>
            <button class="sm primary" onclick={() => (howOpen = true)}>How</button>
            <button class="sm ghost" onclick={notNow}>Not now</button>
        </div>
    {/if}
{:else if !screen.installed && (screen.canPrompt || screen.iosHint)}
    <button class="ghost sm" onclick={() => (screen.canPrompt ? install() : (howOpen = true))}>Install app</button>
{/if}

<Dialog bind:open={howOpen} title="Install on iPhone or iPad" cancelText="Close">
    <ol>
        <li>Open this page in <b>Safari</b>.</li>
        <li>Tap the <b>Share</b> button (the square with an arrow pointing up).</li>
        <li>Tap <b>Add to Home Screen</b>, then <b>Add</b>.</li>
    </ol>
</Dialog>

<style>
    ol {
        margin: 0 0 8px;
        padding-left: 20px;
        line-height: 1.7;
    }
    .nudge {
        display: flex;
        gap: var(--s2);
        align-items: center;
        justify-content: center;
        padding: var(--s1) var(--s2);
        background: var(--panel-2);
        color: var(--text);
        font-size: var(--t-meta);
        text-align: center;
    }
</style>
