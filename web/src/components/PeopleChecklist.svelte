<script lang="ts">
    type Person = { id: string; displayName: string; username?: string };
    /** `value` keeps tap order, so the first person tapped is first in the result. */
    let { people, value = $bindable([]) }: { people: Person[]; value?: string[] } = $props();

    let q = $state("");
    const matches = $derived.by(() => {
        const t = q.trim().toLowerCase();
        const list = t ? people.filter((p) => p.displayName.toLowerCase().includes(t) || (p.username ?? "").toLowerCase().includes(t)) : people;
        return [...list].sort((a, b) => a.displayName.localeCompare(b.displayName));
    });
    function toggle(id: string) {
        value = value.includes(id) ? value.filter((v) => v !== id) : [...value, id];
    }
</script>

<div class="col picker">
    <!-- svelte-ignore a11y_autofocus -->
    <input placeholder="Type a name to search" aria-label="Search people" bind:value={q} autofocus />
    {#if matches.length}
        <div class="table-wrap list">
            <table class="grid">
                <tbody>
                    {#each matches as p (p.id)}
                        <tr class="click" class:picked={value.includes(p.id)} onclick={() => toggle(p.id)}>
                            <td class="tick"
                                ><input
                                    type="checkbox"
                                    tabindex="-1"
                                    aria-label={p.displayName}
                                    checked={value.includes(p.id)}
                                    onclick={(e) => e.preventDefault()}
                                /></td
                            >
                            <td class="ellipsis">{p.displayName}</td>
                            <td class="muted ellipsis">{p.username ?? ""}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {:else}
        <div class="faint">{people.length ? "No match." : "All added."}</div>
    {/if}
</div>

<style>
    .list {
        max-height: 240px;
    }
    .picked {
        background: var(--accent-soft);
    }
    .tick {
        width: 1%;
    }
</style>
