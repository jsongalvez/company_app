<!-- One record in Rows: an optional time, the name with its marks and a line under it, and what can be done at the end. -->
<script lang="ts">
    import type { Snippet } from "svelte";

    type Props = {
        time?: string;
        name: string;
        /** Small marks after the name: walk-in, not sent, a status. */
        marks?: Snippet;
        meta?: string;
        /** Tapping the name and the line under it opens this. */
        href?: string;
        /** Buttons or an amount at the end of the row. */
        end?: Snippet;
        struck?: boolean;
        dim?: boolean;
    };
    let { time, name, marks, meta, href, end, struck = false, dim = false }: Props = $props();
</script>

<div class="k-row" role="row" class:dim>
    {#if time !== undefined}<span class="time" role="cell">{time}</span>{/if}
    <div class="main" role="cell">
        {#if href}
            <a {href}>{@render text()}</a>
        {:else}
            <div class="plain">{@render text()}</div>
        {/if}
    </div>
    {#if end}<div class="end" role="cell">{@render end()}</div>{/if}
</div>

{#snippet text()}
    <span class="name" class:struck
        >{name}{#if marks}&nbsp;{@render marks()}{/if}</span
    >
    {#if meta}<span class="meta">{meta}</span>{/if}
{/snippet}

<style>
    .k-row {
        display: flex;
        align-items: center;
        gap: var(--s3);
        min-height: var(--row);
        padding: 0 var(--s4);
        border-bottom: 1px solid var(--line);
    }
    .k-row:last-child {
        border-bottom: 0;
    }
    .dim {
        opacity: 0.6;
    }
    .time {
        flex: none;
        width: var(--s6);
        min-width: max-content;
        font-size: var(--t-meta);
        color: var(--text-2);
    }
    .main {
        flex: 1;
        min-width: 0;
        align-self: stretch;
        display: flex;
    }
    a,
    .plain {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        justify-content: center;
        min-height: var(--tap);
        padding: var(--s2) 0;
        color: inherit;
        text-decoration: none;
    }
    .name {
        font-size: var(--t-row);
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .struck {
        text-decoration: line-through;
    }
    .meta {
        font-size: var(--t-meta);
        color: var(--text-2);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .end {
        flex: none;
        display: flex;
        align-items: center;
        gap: var(--s2);
    }
</style>
