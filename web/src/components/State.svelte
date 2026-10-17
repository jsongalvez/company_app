<script lang="ts">
    /** Loading / error line for a Query. Renders nothing once data is present. */
    let {
        q,
        empty = false,
        emptyText = "Nothing here yet.",
    }: { q: { loading: boolean; error: string | null; refresh: () => void }; empty?: boolean; emptyText?: string } = $props();
</script>

{#if q.error}
    <div class="notice bad row">{q.error}<span class="spacer"></span><button class="sm" onclick={() => q.refresh()}>Retry</button></div>
{:else if q.loading}
    <div class="faint pad">Loading…</div>
{:else if empty}
    <div class="faint pad">{emptyText}</div>
{/if}

<style>
    .pad {
        padding: 8px 2px;
    }
</style>
