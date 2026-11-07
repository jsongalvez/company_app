<!-- The screen's main actions, held at the bottom edge where a thumb reaches. The first one gets the most room. -->
<script lang="ts">
    import type { Snippet } from "svelte";

    let { children }: { children: Snippet } = $props();
</script>

<div class="k-dock" data-one-row>{@render children()}</div>

<style>
    .k-dock {
        position: sticky;
        bottom: 0;
        z-index: 2;
        display: flex;
        gap: var(--s2);
        margin-top: auto;
        padding: var(--s2) var(--s4);
        background: var(--panel);
        border-top: 1px solid var(--line);
    }
    .k-dock > :global(*) {
        flex: 1 1 0;
        min-width: 0;
    }
    .k-dock > :global(:first-child) {
        flex-grow: 1.4;
    }
</style>
