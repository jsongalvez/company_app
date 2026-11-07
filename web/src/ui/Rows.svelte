<!-- A list of Row. Rows of one kind of record (sessions, visits) read as a table to screen readers and tests. -->
<script lang="ts">
    import type { Snippet } from "svelte";

    let { label, children }: { label: string; children: Snippet } = $props();
</script>

<div class="k-rows" role="table" aria-label={label}>{@render children()}</div>

<style>
    .k-rows {
        background: var(--panel);
        border-top: 1px solid var(--line);
        border-bottom: 1px solid var(--line);
    }
</style>
