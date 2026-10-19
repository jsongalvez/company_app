<script lang="ts">
    import { screen, toggleFull } from "../lib/screen.svelte";
    const tip = $derived(screen.full ? "Leave full screen" : "Full screen");
</script>

{#if screen.canFull}
    <button class="icon full" onclick={toggleFull} title={tip} aria-label={tip}>
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="square">
            {#if screen.full}
                <path d="M6 1.5V6H1.5M10 1.5V6h4.5M6 14.5V10H1.5M10 14.5V10h4.5" />
            {:else}
                <path d="M1.5 6V1.5H6M10 1.5h4.5V6M1.5 10v4.5H6M14.5 10v4.5H10" />
            {/if}
        </svg>
    </button>
{/if}

<style>
    .icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        padding: 0;
    }
</style>
