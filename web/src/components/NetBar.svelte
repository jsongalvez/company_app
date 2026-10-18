<script lang="ts">
    import { net } from "../lib/net.svelte";
    import { outbox, waiting, failed, discard, flush } from "../lib/outbox.svelte";
    import { time } from "../lib/format";

    const w = $derived((outbox.items, waiting()));
    const f = $derived((outbox.items, failed()));
    const offline = $derived(!net.online || net.trouble);
    let open = $state(false);

    function drop(id: string, what: string) {
        if (confirm(`Throw away "${what}"? It won't be sent.`)) discard(id);
    }
</script>

{#if offline || w.length || f.length}
    <div class="dock">
        <div class="netbar" class:bad={offline || f.length > 0} role="status">
            <span>
                {#if !net.online}No connection.{:else if net.trouble}Weak connection.{/if}
                {#if w.length}
                    {w.length === 1 ? "1 change" : `${w.length} changes`} waiting to send{outbox.sending ? "…" : "."}
                {:else if offline}
                    New sessions, sales and marks will wait on this phone until you're back online.
                {/if}
                {#if f.length}<b>{f.length} couldn't be sent.</b>{/if}
            </span>
            {#if w.length || f.length}
                <button class="sm" onclick={() => (open = !open)} aria-expanded={open}>{open ? "Hide" : "See"}</button>
            {/if}
        </div>
        {#if open && (w.length || f.length)}
            <div class="outbox" role="region" aria-label="Changes waiting to send">
                {#if f.length}
                    <h3>Couldn't be sent</h3>
                    <p class="muted">Someone may have changed these while you were offline. Check the page, redo it if needed, then throw it away.</p>
                    <ul>
                        {#each f as i (i.id)}
                            <li>
                                <span><b>{i.what}</b> · {time(i.at)}<br /><span class="err">{i.failed}</span></span>
                                <button class="sm" onclick={() => drop(i.id, i.what)}>Throw away</button>
                            </li>
                        {/each}
                    </ul>
                {/if}
                {#if w.length}
                    <h3>Waiting to send</h3>
                    <ul>
                        {#each w as i (i.id)}
                            <li>
                                <span><b>{i.what}</b> · {time(i.at)}</span>
                                <button class="sm ghost" onclick={() => drop(i.id, i.what)}>Throw away</button>
                            </li>
                        {/each}
                    </ul>
                    <button class="sm" onclick={() => flush()} disabled={outbox.sending}>Send now</button>
                {/if}
            </div>
        {/if}
    </div>
{/if}

<style>
    .dock {
        position: fixed;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 50;
        display: flex;
        flex-direction: column-reverse;
    }
    .netbar {
        display: flex;
        gap: 8px;
        align-items: center;
        justify-content: center;
        padding: 6px 10px;
        background: var(--warn-soft, var(--panel));
        color: var(--warn);
        border-top: 1px solid var(--warn);
        font-size: 13px;
        font-weight: 600;
        text-align: center;
    }
    .netbar.bad {
        background: var(--bad-soft);
        color: var(--bad);
        border-top-color: var(--bad);
    }
    .outbox {
        max-height: 50vh;
        overflow: auto;
        padding: 8px 12px;
        background: var(--panel);
        border-top: 1px solid var(--line, var(--bad));
        font-size: 13px;
    }
    .outbox h3 {
        margin: 4px 0;
        font-size: 13px;
    }
    .outbox ul {
        list-style: none;
        margin: 0 0 8px;
        padding: 0;
    }
    .outbox li {
        display: flex;
        justify-content: space-between;
        gap: 8px;
        align-items: center;
        padding: 4px 0;
        border-bottom: 1px solid var(--line, transparent);
    }
    .err {
        color: var(--bad);
    }
</style>
