<!-- One line the screen needs read before anything else. With onclick the whole line is a button that ends in ›. -->
<script lang="ts">
    import type { Snippet } from "svelte";

    type Props = {
        tone?: "warn" | "info" | "bad";
        onclick?: () => void;
        onpointerdown?: () => void;
        /** Buttons at the end of a line that is not itself a button. */
        end?: Snippet;
        /** "status" when the line appears on its own and should be read out. */
        role?: "status";
        children: Snippet;
    };
    let { tone = "info", onclick, onpointerdown, end, role, children }: Props = $props();
</script>

{#if onclick}
    <button class="k-notice" data-tone={tone} {onclick} {onpointerdown}><span class="text">{@render children()}</span><span aria-hidden="true">›</span></button>
{:else}
    <div class="k-notice" data-tone={tone} {role}>
        <span class="text">{@render children()}</span>{#if end}{@render end()}{/if}
    </div>
{/if}

<style>
    .k-notice {
        display: flex;
        align-items: center;
        gap: var(--s2);
        width: 100%;
        height: auto;
        min-height: var(--tap);
        margin: 0;
        padding: var(--s2) var(--s3);
        border: 0;
        border-left: var(--rule) solid var(--accent);
        background: var(--accent-soft);
        color: var(--text);
        font: inherit;
        font-size: var(--t-body);
        text-align: left;
        white-space: normal;
    }
    button.k-notice {
        cursor: pointer;
    }
    [data-tone="warn"] {
        border-left-color: var(--warn);
        background: var(--warn-soft);
    }
    [data-tone="bad"] {
        border-left-color: var(--bad);
        background: var(--bad-soft);
    }
    .text {
        flex: 1;
        min-width: 0;
    }
</style>
