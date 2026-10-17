<script lang="ts">
    import { app } from "../lib/app.svelte";
</script>

{#if app.toast}
    <div class="toast notice {app.toast.kind}" role="status" aria-live="polite">
        {app.toast.text}
        <button class="ghost sm" onclick={() => (app.toast = null)} aria-label="Dismiss">×</button>
    </div>
{/if}

<style>
    .toast {
        position: fixed;
        right: 12px;
        bottom: 12px;
        z-index: 100;
        max-width: min(420px, calc(100vw - 24px));
        display: flex;
        align-items: center;
        gap: 10px;
        box-shadow: 0 2px 8px rgb(0 0 0 / 0.15);
    }
</style>
