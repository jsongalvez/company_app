<script lang="ts">
    /** Loading / error line for a Query. Renders nothing once data is present. */
    let {
        q,
        empty = false,
        emptyText = "Nothing here yet.",
    }: { q: { loading: boolean; waiting?: boolean; error: string | null; refresh: () => void }; empty?: boolean; emptyText?: string } = $props();
    import { net } from "../lib/net.svelte";

    // Loading that drags on says so, so nobody thinks the app froze.
    let slow = $state(false);
    $effect(() => {
        slow = false;
        if (!q.loading) return;
        const t = setTimeout(() => (slow = true), 3000);
        return () => clearTimeout(t);
    });
</script>

{#if q.error}
    <div class="notice bad row">{q.error}<span class="spacer"></span><button class="sm" onclick={() => q.refresh()}>Retry</button></div>
{:else if q.waiting}
    <div class="faint pad row"><span class="spin" aria-hidden="true"></span>Offline</div>
{:else if q.loading}
    <div class="faint pad row">
        <span class="spin" aria-hidden="true"></span>{!net.online ? "Offline" : slow || net.trouble ? "Slow connection…" : "Loading…"}
    </div>
{:else if empty}
    <div class="faint pad">{emptyText}</div>
{/if}

<style>
    .pad {
        padding: 8px 2px;
    }
</style>
