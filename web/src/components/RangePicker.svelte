<script lang="ts" module>
    export type Range = { from: string; to: string; last: string };
    export type Preset = { key: string; label: string; range?: () => { from: string; to: string } };
</script>

<script lang="ts">
    import { wallTime } from "../lib/format";

    // One button for a span of time, like CloudWatch: quick choices plus exact ends.
    // mode "time" takes hours, minutes and seconds; "day" takes whole days.
    // A preset with no range() stays relative ("last") and is worked out when used.
    let {
        mode = "time",
        from = "",
        to = "",
        last = "",
        presets,
        max = "",
        clearable = true,
        onchange,
    }: {
        mode?: "time" | "day";
        from?: string;
        to?: string;
        last?: string;
        presets: Preset[];
        max?: string;
        clearable?: boolean;
        onchange: (r: Range) => void;
    } = $props();

    let open = $state(false);
    let btn = $state<HTMLButtonElement>();
    let panel = $state<HTMLDivElement>();
    let pos = $state({ top: 0, left: 0, width: 360 });
    let f = $state("");
    let t = $state("");
    let err = $state("");

    const text = $derived.by(() => {
        const p = presets.find((p) => p.key === last);
        if (p) return p.label;
        if (!from && !to) return mode === "time" ? "Any time" : "Pick days";
        if (from && to && from === to && mode === "day") return wallTime(from);
        return `${from ? wallTime(from) : "Start"} – ${to ? wallTime(to) : "now"}`;
    });

    function place() {
        if (!btn) return;
        const r = btn.getBoundingClientRect();
        const width = Math.min(380, window.innerWidth - 16);
        pos = { top: r.bottom + 4, left: Math.max(8, Math.min(r.left, window.innerWidth - width - 8)), width };
    }
    function toggle() {
        open = !open;
        if (open) {
            f = from;
            t = to;
            err = "";
            place();
        }
    }
    function pick(p: Preset) {
        open = false;
        onchange(p.range ? { ...p.range(), last: "" } : { from: "", to: "", last: p.key });
    }
    function apply(e: Event) {
        e.preventDefault();
        let a = f,
            b = t;
        if (a && b && a > b) [a, b] = [b, a];
        if (!a && !b && !clearable) {
            err = "Pick a start or an end.";
            return;
        }
        open = false;
        onchange({ from: a, to: b, last: "" });
    }
    function clear() {
        open = false;
        onchange({ from: "", to: "", last: "" });
    }
    function outside(e: MouseEvent) {
        if (open && !panel?.contains(e.target as Node) && !btn?.contains(e.target as Node)) open = false;
    }
    const inputType = $derived(mode === "time" ? "datetime-local" : "date");
    const maxAttr = $derived(max || undefined);
</script>

<svelte:window onclick={outside} onkeydown={(e) => e.key === "Escape" && (open = false)} onresize={() => open && place()} onscroll={() => open && place()} />

<button type="button" class="range-btn" class:on={open} bind:this={btn} onclick={toggle} aria-haspopup="dialog" aria-expanded={open} title="Pick a time span">
    <span class="ellipsis">{text}</span><span class="faint" aria-hidden="true">▾</span>
</button>

{#if open}
    <div class="range-panel" bind:this={panel} role="dialog" aria-label="Time span" style="top:{pos.top}px;left:{pos.left}px;width:{pos.width}px">
        <div class="presets">
            {#each presets as p (p.key)}
                <button type="button" class:on={p.key === last} onclick={() => pick(p)}>{p.label}</button>
            {/each}
        </div>
        <form onsubmit={apply}>
            <label class="field">From<input type={inputType} step={mode === "time" ? 1 : undefined} bind:value={f} max={maxAttr} aria-label="From" /></label>
            <label class="field">To<input type={inputType} step={mode === "time" ? 1 : undefined} bind:value={t} max={maxAttr} aria-label="To" /></label>
            {#if err}<div class="notice bad">{err}</div>{/if}
            <div class="row">
                {#if clearable}<button type="button" onclick={clear}>Any time</button>{/if}
                <span class="spacer"></span>
                <button type="button" onclick={() => (open = false)}>Cancel</button>
                <button type="submit" class="primary">Apply</button>
            </div>
        </form>
    </div>
{/if}

<style>
    .range-btn {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        max-width: 100%;
        min-width: 0;
        justify-content: space-between;
    }
    .range-panel {
        position: fixed;
        z-index: 50;
        background: var(--panel);
        border: 1px solid var(--line-strong);
        padding: 10px;
        display: grid;
        gap: 10px;
        box-shadow: 0 6px 18px rgb(0 0 0 / 0.18);
    }
    .presets {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
        gap: 4px;
    }
    .presets button {
        padding-inline: 6px;
    }
    .presets button.on {
        background: var(--accent-soft);
        border-color: var(--accent);
    }
    form {
        display: grid;
        gap: 8px;
        border-top: 1px solid var(--line);
        padding-top: 10px;
    }
    form input {
        width: 100%;
        min-width: 0;
    }
</style>
