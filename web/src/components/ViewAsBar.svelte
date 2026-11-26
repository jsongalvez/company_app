<svelte:options runes />

<script lang="ts">
    import { app, stopViewAs, setViewAsChanges } from "../lib/app.svelte";
</script>

{#if app.viewAs}
    <div class="viewas" role="status">
        <span class="ellipsis"
            >Viewing as <b>{app.viewAs.name}</b>{#if app.viewAs.roles.length}&nbsp;({app.viewAs.roles.join(", ")}){/if}</span
        >
        <label class="check nowrap"
            ><input type="checkbox" checked={app.viewAs.allowChanges} onchange={(e) => setViewAsChanges(e.currentTarget.checked)} />Allow changes</label
        >
        <button class="sm" onclick={() => stopViewAs()}>Back to me</button>
    </div>
{/if}

<style>
    .viewas {
        grid-column: 1 / -1;
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 4px 10px;
        background: var(--warn);
        color: var(--panel);
        font-size: var(--t-meta);
        min-width: 0;
    }
    .viewas > span {
        flex: 1;
        min-width: 0;
    }
    .viewas .check {
        color: inherit;
    }

    /* Phone: the name takes its own line. */
    @media (max-width: 760px) {
        .viewas {
            flex-wrap: wrap;
            row-gap: 2px;
        }
        .viewas > span {
            flex-basis: 100%;
        }
    }
</style>
