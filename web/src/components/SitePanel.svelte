<script lang="ts">
    import { BATTERY_LOW, findHub, isUnsent, site, siteView } from "../lib/site.svelte";
    import type { ServerSessionLike } from "../lib/site-view";
    import { time } from "../lib/format";

    let { sessions = [] }: { sessions?: readonly ServerSessionLike[] } = $props();

    const serverById = $derived(new Map(sessions.map((s) => [s.id, s])));
    const view = $derived((site.entries, siteView(sessions)));
    const ago = (s: number) => (s < 60 ? "just now" : `${Math.floor(s / 60)} min ago`);
</script>

{#if !site.lastAt && site.offSignal && site.localNetwork === "prompt"}
    <section class="site" aria-label="Site">
        <h2>Site hub</h2>
        <button type="button" class="find" onclick={findHub}>Find site hub</button>
    </section>
{:else if !site.lastAt && site.offSignal && site.localNetwork === "denied"}
    <section class="site" aria-label="Site">
        <h2>Site hub: local network blocked</h2>
        <p class="note">Allow in site settings</p>
    </section>
{:else if site.lastAt}
    <section class="site" aria-label="Site">
        <h2>
            {#if site.connected}
                Site hub: connected, {site.phones.length === 1 ? "1 phone" : `${site.phones.length} phones`}
            {:else}
                Site hub: not here since {time(new Date(site.lastAt).toISOString())}
            {/if}
        </h2>
        {#if site.battery && !site.battery.charging && site.battery.percent <= BATTERY_LOW}
            <p class="low" role="status">Site hub battery low: {site.battery.percent}%</p>
        {/if}
        <div class="cols">
            <div>
                <h3>Waiting</h3>
                <ul aria-label="Waiting at the site">
                    {#each view.waiting as e (e.opId)}
                        <li>
                            <b>{e.client}</b> · {e.name} · {time(e.at)}
                            {#if isUnsent(e, serverById)}<span class="unsent">not sent yet</span>{/if}
                        </li>
                    {:else}
                        <li class="none">None</li>
                    {/each}
                </ul>
            </div>
            {#if view.done.length}
                <div>
                    <h3>Done</h3>
                    <ul aria-label="Done at the site">
                        {#each view.done as e (e.opId)}
                            <li>
                                <b>{e.client ?? "Session"}</b> · {e.name} · {time(e.at)}
                                {#if isUnsent(e, serverById)}<span class="unsent">not sent yet</span>{/if}
                            </li>
                        {/each}
                    </ul>
                </div>
            {/if}
            <div>
                <h3>On duty</h3>
                <ul aria-label="On duty at the site">
                    {#each view.onDuty as e (e.opId)}
                        <li>{e.name} · {time(e.at)}</li>
                    {:else}
                        <li class="none">None</li>
                    {/each}
                </ul>
            </div>
            <div>
                <h3>Sales</h3>
                <ul aria-label="Sales at the site">
                    {#each view.sales as e (e.opId)}
                        <li>
                            {e.quantity} sold · {e.name} · {time(e.at)}
                            {#if isUnsent(e, serverById)}<span class="unsent">not sent yet</span>{/if}
                        </li>
                    {:else}
                        <li class="none">None</li>
                    {/each}
                </ul>
            </div>
            <div>
                <h3>Phones</h3>
                <ul aria-label="Phones at the site">
                    {#each site.phones as p (p.device)}
                        <li>{p.name} · {ago(p.agoSec)} · {p.unsent} unsent</li>
                    {/each}
                    {#each site.notSeen as m (m.userId)}
                        <li class="missing">{m.name} · not seen</li>
                    {/each}
                </ul>
            </div>
        </div>
    </section>
{/if}

<style>
    .site {
        margin: 0 0 var(--s3);
        padding: var(--s2) var(--s3);
        background: var(--panel);
        border: 1px solid var(--line);
        border-radius: var(--s2);
    }
    h2 {
        margin: 0 0 var(--s2);
        font-size: var(--t-body);
    }
    h3 {
        margin: 0 0 var(--s1);
        font-size: var(--t-meta);
        color: var(--text-2);
    }
    .cols {
        display: flex;
        flex-wrap: wrap;
        gap: var(--s3);
    }
    .cols > div {
        flex: 1 1 auto;
    }
    ul {
        list-style: none;
        margin: 0;
        padding: 0;
        font-size: var(--t-body);
    }
    li {
        padding: var(--s1) 0;
    }
    .none {
        color: var(--text-3);
    }
    .unsent {
        margin-left: var(--s1);
        color: var(--warn);
        font-size: var(--t-meta);
    }
    .low {
        margin: 0 0 var(--s2);
        color: var(--bad);
        font-weight: 600;
        font-size: var(--t-body);
    }
    .find {
        min-height: var(--tap);
        font-size: var(--t-body);
    }
    .note {
        margin: 0;
        color: var(--text-2);
        font-size: var(--t-body);
    }
    .missing {
        color: var(--bad);
    }
</style>
