<script lang="ts">
    import type { Snippet } from "svelte";
    let { title, sub = "", children }: { title: string; sub?: string; children?: Snippet } = $props();
</script>

<div class="head">
    <h1>{title}</h1>
    {#if sub}<span class="muted">{sub}</span>{/if}
    <span class="spacer"></span>
    <div class="row wrap">{@render children?.()}</div>
</div>

<style>
    .head {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;
        min-height: 28px;
        margin-bottom: 10px;
    }
</style>
