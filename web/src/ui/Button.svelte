<script lang="ts">
    import type { Snippet } from "svelte";
    import type { HTMLButtonAttributes } from "svelte/elements";

    type Props = Omit<HTMLButtonAttributes, "class"> & {
        /** plain: a bordered button; primary: the one main action on a screen; quiet: no border, for row actions. */
        kind?: "plain" | "primary" | "quiet" | "danger";
        /** The taller size for the dock at the bottom of a screen. */
        big?: boolean;
        /** Given, the button is a link that opens this page. */
        href?: string;
        children: Snippet;
    };
    let { kind = "plain", big = false, type = "button", href, children, ...rest }: Props = $props();
</script>

{#if href}
    <a {href} class="k-button" data-kind={kind} class:big title={rest.title} aria-label={rest["aria-label"]}>{@render children()}</a>
{:else}
    <button {type} class="k-button" data-kind={kind} class:big {...rest}>{@render children()}</button>
{/if}

<style>
    .k-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--s2);
        height: auto;
        min-height: var(--tap);
        min-width: var(--tap);
        padding: 0 var(--s4);
        border: 1px solid var(--line-strong);
        background: var(--panel);
        color: var(--text);
        font: inherit;
        font-size: var(--t-body);
        text-decoration: none;
        white-space: nowrap;
        cursor: pointer;
    }
    .big {
        min-height: var(--tap-big);
    }
    [data-kind="primary"] {
        background: var(--accent);
        border-color: var(--accent);
        color: var(--accent-text);
        font-weight: 600;
    }
    [data-kind="quiet"] {
        background: transparent;
        border-color: transparent;
        color: var(--text-2);
        padding: 0 var(--s2);
    }
    [data-kind="danger"] {
        color: var(--bad);
        border-color: var(--bad);
    }
    .k-button:disabled {
        opacity: 0.45;
        cursor: default;
    }
    .k-button:focus-visible {
        outline: 2px solid var(--focus);
        outline-offset: 2px;
    }
</style>
