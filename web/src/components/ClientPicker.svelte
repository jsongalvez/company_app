<script lang="ts">
    import { ApiError, type S } from "../api/client";
    import { fetchCached } from "../lib/cache.svelte";
    import { searchLocal } from "../lib/clientdir.svelte";
    import { load } from "../lib/data";
    import { clientName } from "../lib/format";

    let { value = $bindable(null), onnew }: { value?: S["ClientResponse"] | null; onnew?: (q: string) => void } = $props();

    let q = $state("");
    let results = $state<S["ClientResponse"][]>([]);
    let searching = $state(false);
    /** The server couldn't be asked: these are only the clients saved on this phone. */
    let localOnly = $state(false);
    let timer: ReturnType<typeof setTimeout> | undefined;

    function onInput() {
        clearTimeout(timer);
        const term = q.trim();
        if (term.length < 2) {
            results = [];
            searching = false;
            return;
        }
        // Clients saved on the phone show at once; the server's answer follows when it can.
        const local = searchLocal(term);
        results = local;
        localOnly = !navigator.onLine;
        if (!navigator.onLine) {
            searching = false;
            return;
        }
        searching = true;
        timer = setTimeout(async () => {
            const l = load.clients(term);
            try {
                const r = await fetchCached(l.key, l.fn);
                if (q.trim() !== term) return;
                const ids = new Set(r.map((c) => c.id));
                results = [...r, ...searchLocal(term).filter((c) => !ids.has(c.id))];
                localOnly = false;
            } catch (e) {
                if (q.trim() === term) localOnly = e instanceof ApiError && e.status <= 0;
            } finally {
                if (q.trim() === term) searching = false;
            }
        }, 180);
    }
</script>

{#if value}
    <div class="picked row">
        <b class="grow ellipsis">{clientName(value)}</b>
        <span class="muted">{value.gender === "UNKNOWN" ? "" : value.gender} {value.age}</span>
        <span class="muted">{value.phoneNumber ?? ""}</span>
        <button type="button" class="sm" onclick={() => (value = null)}>Change</button>
    </div>
{:else}
    <div class="col picker">
        <div class="row">
            <input class="grow" placeholder="Search by name or phone (2+ letters)" bind:value={q} oninput={onInput} />
            {#if onnew}<button type="button" onclick={() => onnew?.(q.trim())}>New client</button>{/if}
        </div>
        {#if results.length}
            <div class="table-wrap list">
                <table class="grid">
                    <tbody>
                        {#each results as c (c.id)}
                            <tr class="click" onclick={() => (value = c)}>
                                <td class="ellipsis">{clientName(c)}</td>
                                <td>{c.gender === "UNKNOWN" ? "" : c.gender}</td>
                                <td class="num">{c.age}</td>
                                <td class="muted">{c.phoneNumber ?? ""}</td>
                                <td class="num faint">{c.sessionCount} visits</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        {:else if q.trim().length >= 2 && searching}
            <div class="faint row"><span class="spin" aria-hidden="true"></span>Searching…</div>
        {:else if q.trim().length >= 2}
            <div class="faint">
                No match{localOnly ? " on this phone" : ""}.
            </div>
        {/if}
        {#if localOnly && results.length}<div class="faint">Offline: saved clients only.</div>{/if}
    </div>
{/if}

<style>
    .picked {
        border: 1px solid var(--accent);
        background: var(--accent-soft);
        padding: 4px 6px;
    }
    .list {
        max-height: 220px;
    }
</style>
