<!-- A screen built on the kit. While one is shown, text starts at the kit's body size; older screens keep theirs until rebuilt. -->
<script lang="ts">
    import type { Snippet } from "svelte";

    /** The page's name, as the menu calls it. Read out, not shown: the screen's first line says where you are. */
    let { title, children }: { title: string; children: Snippet } = $props();
</script>

<div class="k-page">
    <h1 class="title">{title}</h1>
    {@render children()}
</div>

<style>
    :global(body:has(.k-page)) {
        font-size: var(--t-body);
        line-height: var(--lh);
    }
    .title {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
        white-space: nowrap;
    }
    .k-page {
        display: flex;
        flex-direction: column;
        gap: var(--s3);
        max-width: var(--page-max);
        min-height: 100%;
        margin: 0 auto;
        padding-bottom: var(--netbar-h, 0);
    }
</style>
