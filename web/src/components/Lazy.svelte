<script lang="ts">
    import { page, type PageName } from "../lib/pages.svelte";

    let { name, ...props }: { name: PageName; id?: string } = $props();
    const Page = $derived(page(name));
</script>

{#if Page}
    <Page {...props} />
{/if}
