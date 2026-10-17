<script lang="ts">
    type Person = { id: string; displayName: string; username?: string; roles?: string[] };
    let { people, value = $bindable("") }: { people: Person[]; value?: string } = $props();

    let q = $state("");
    const picked = $derived(people.find((p) => p.id === value) ?? null);
    const matches = $derived.by(() => {
        const t = q.trim().toLowerCase();
        const list = t ? people.filter((p) => p.displayName.toLowerCase().includes(t) || (p.username ?? "").toLowerCase().includes(t)) : people;
        return [...list].sort((a, b) => a.displayName.localeCompare(b.displayName));
    });
</script>

{#if picked}
    <div class="picked row">
        <b class="grow ellipsis">{picked.displayName}</b>
        {#if picked.username}<span class="muted ellipsis">{picked.username}</span>{/if}
        <button type="button" class="sm" onclick={() => (value = "")}>Change</button>
    </div>
{:else}
    <div class="col picker">
        <!-- svelte-ignore a11y_autofocus -->
        <input placeholder="Type a name to search" aria-label="Search people" bind:value={q} autofocus />
        {#if matches.length}
            <div class="table-wrap list">
                <table class="grid">
                    <tbody>
                        {#each matches as p (p.id)}
                            <tr class="click" onclick={() => ((value = p.id), (q = ""))}>
                                <td class="ellipsis">{p.displayName}</td>
                                <td class="muted ellipsis">{p.username ?? ""}</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        {:else}
            <div class="faint">{people.length ? "No match." : "Everyone is already added."}</div>
        {/if}
    </div>
{/if}

<style>
    .picked {
        border: 1px solid var(--accent);
        background: var(--accent-soft);
        padding: 4px 6px;
    }
    .list {
        max-height: 240px;
    }
</style>
