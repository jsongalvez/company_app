<!-- A status word in a thin frame of its own colour, so it never relies on colour alone. -->
<script lang="ts">
    import type { Snippet } from "svelte";

    type Props = { tone?: "ok" | "warn" | "bad" | "info" | "plain"; title?: string; children: Snippet };
    let { tone = "plain", title, children }: Props = $props();
</script>

<span class="k-mark" data-tone={tone} {title}>{@render children()}</span>

<style>
    .k-mark {
        display: inline-block;
        padding: 0 var(--s1);
        border: 1px solid currentColor;
        font-size: var(--t-meta);
        font-weight: 400;
        line-height: var(--lh-tight);
        color: var(--text-2);
        vertical-align: 1px;
    }
    [data-tone="ok"] {
        color: var(--ok);
    }
    [data-tone="warn"] {
        color: var(--warn);
    }
    [data-tone="bad"] {
        color: var(--bad);
    }
    [data-tone="info"] {
        color: var(--accent);
    }
</style>
