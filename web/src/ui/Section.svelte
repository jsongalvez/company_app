<script lang="ts">
    import type { Snippet } from "svelte";

    type Props = {
        title: string;
        /** Shown after the title: "Here now · 3". */
        count?: number;
        /** Quiet text at the end of the header. */
        aside?: string;
        /** Given, a tap anywhere on the header folds or unfolds what's under it. */
        fold?: { open: boolean; toggle: () => void };
        children?: Snippet;
    };
    let { title, count, aside, fold, children }: Props = $props();
    const open = $derived(!fold || fold.open);
</script>

<section class="k-section" aria-label={title}>
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <header class:folds={!!fold} onclick={fold?.toggle}>
        <h2>
            {#if fold}<button aria-expanded={open}>{title}</button>{:else}{title}{/if}
        </h2>
        {#if count !== undefined}<span class="n">· {count}</span>{/if}
        <span class="grow"></span>
        {#if aside}<span class="aside">{aside}</span>{/if}
    </header>
    {#if open && children}{@render children()}{/if}
</section>

<style>
    header {
        display: flex;
        align-items: center;
        gap: var(--s2);
        min-height: var(--tap);
        padding: 0 var(--s4);
    }
    h2,
    .n {
        font-size: var(--t-meta);
        font-weight: 600;
        letter-spacing: 0.05em;
        color: var(--text-2);
    }
    h2 {
        display: flex;
        align-items: center;
        margin: 0;
        text-transform: uppercase;
    }
    .grow {
        flex: 1;
    }
    .aside {
        font-size: var(--t-meta);
        color: var(--text-2);
    }
    .folds {
        cursor: pointer;
    }
    button {
        all: unset;
        display: inline-flex;
        align-items: center;
        min-height: var(--tap);
        cursor: pointer;
    }
    button::before {
        content: "▸" / "";
        width: var(--s4);
    }
    button[aria-expanded="true"]::before {
        content: "▾" / "";
    }
    button:focus-visible {
        outline: 2px solid var(--focus);
    }
</style>
