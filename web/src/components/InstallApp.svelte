<script lang="ts">
    import { screen, install } from "../lib/screen.svelte";
    import Dialog from "./Dialog.svelte";
    let howOpen = $state(false);
</script>

{#if !screen.installed && (screen.canPrompt || screen.iosHint)}
    <div class="install">
        <button class="sm" onclick={() => (screen.canPrompt ? install() : (howOpen = true))}>Install app</button>
        <span class="faint">Opens from its own icon, without browser bars.</span>
    </div>
{/if}

<Dialog bind:open={howOpen} title="Install on iPhone or iPad" cancelText="Close">
    <ol>
        <li>Open this page in <b>Safari</b>.</li>
        <li>Tap the <b>Share</b> button (the square with an arrow pointing up).</li>
        <li>Tap <b>Add to Home Screen</b>, then <b>Add</b>.</li>
    </ol>
    <p class="faint">Then open CompanyApp from its new icon. It fills the screen and works with weak signal, like the browser tab.</p>
</Dialog>

<style>
    .install {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 10px 12px;
        margin-top: auto;
        border-top: 1px solid var(--line);
        font-size: 12px;
    }
    .install button {
        align-self: flex-start;
    }
    ol {
        margin: 0 0 8px;
        padding-left: 20px;
        line-height: 1.7;
    }
</style>
