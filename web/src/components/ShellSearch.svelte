<script lang="ts">
    import { go } from "../lib/router.svelte";
    import { clientName } from "../lib/format";
    import { searchLocal } from "../lib/clientdir.svelte";

    /** `onpick`: a suggestion was opened (the phone menu closes). */
    let { onpick }: { onpick: () => void } = $props();

    let q = $state("");
    let qFocus = $state(false);
    // Suggestions come from the client list kept on the phone, so they show at once, offline too.
    const hints = $derived(qFocus && q.trim().length >= 2 ? searchLocal(q, 6) : []);
    function search(e: SubmitEvent) {
        e.preventDefault();
        qFocus = false;
        go("/clients", { q: q.trim() });
    }
    function pick(id: string) {
        qFocus = false;
        q = "";
        onpick();
        go(`/clients/${id}`);
    }
</script>

<form class="search" onsubmit={search} role="search">
    <input
        type="search"
        placeholder="Find client by name or phone"
        bind:value={q}
        aria-label="Find client"
        autocomplete="off"
        onfocus={() => (qFocus = true)}
        onblur={() => setTimeout(() => (qFocus = false), 150)}
        onkeydown={(e) => e.key === "Escape" && (qFocus = false)}
    />
    <button type="submit">Find</button>
    {#if hints.length}
        <ul class="hints" role="listbox" aria-label="Matching clients">
            {#each hints as c (c.id)}
                <li role="option" aria-selected="false">
                    <button type="button" onmousedown={(e) => e.preventDefault()} onclick={() => pick(c.id)}
                        ><span class="ellipsis">{clientName(c)}</span><span class="faint"
                            >{c.gender} {c.age ?? ""}{c.phoneNumber ? ` · ${c.phoneNumber}` : ""}</span
                        ></button
                    >
                </li>
            {/each}
        </ul>
    {/if}
</form>

<style>
    .search {
        display: flex;
        width: 300px;
        min-width: 140px;
    }
    .search input {
        flex: 1;
    }
    .search {
        position: relative;
    }
    .hints {
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        z-index: 60;
        margin: 2px 0 0;
        padding: 0;
        list-style: none;
        background: var(--panel);
        border: 1px solid var(--line-strong);
        box-shadow: 0 4px 12px rgb(0 0 0 / 0.15);
    }
    .hints button {
        display: flex;
        width: 100%;
        gap: var(--s2);
        justify-content: space-between;
        border: 0;
        border-bottom: 1px solid var(--line);
        border-radius: 0;
        background: none;
        text-align: left;
        height: auto;
        padding: var(--s2);
        min-width: 0;
    }
    .hints button:hover,
    .hints button:focus {
        background: var(--line);
    }
    .search button {
        margin-left: -1px;
    }

    /* Tablet: a narrower box. */
    @media (max-width: 1100px) {
        .search {
            width: 200px;
        }
    }

    /* Phone: the search takes its own row under the bar. */
    @media (max-width: 760px) {
        .search {
            order: 10;
            flex: 1 1 220px;
            width: auto;
            min-width: 0;
        }
    }
</style>
