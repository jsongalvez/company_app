<script lang="ts">
    import { theme, toggleTheme } from "../lib/theme.svelte";
    const next = $derived(theme.value === "dark" ? "day" : "night");
</script>

<button class="icon" onclick={toggleTheme} title="Switch to {next} mode" aria-label="Switch to {next} mode">
    {#if theme.value === "dark"}
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"
            ><circle cx="8" cy="8" r="3.2" fill="currentColor" /><g stroke="currentColor" stroke-width="1.5" stroke-linecap="square"
                ><path d="M8 0.8v2M8 13.2v2M0.8 8h2M13.2 8h2M2.9 2.9l1.4 1.4M11.7 11.7l1.4 1.4M2.9 13.1l1.4-1.4M11.7 4.3l1.4-1.4" /></g
            ></svg
        >
    {:else}
        <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"
            ><path d="M10.5 1.5a6.5 6.5 0 1 0 4 10.6A5.5 5.5 0 0 1 10.5 1.5z" fill="currentColor" /></svg
        >
    {/if}
</button>

<style>
    .icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        padding: 0;
    }
</style>
