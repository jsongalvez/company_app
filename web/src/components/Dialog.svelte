<script lang="ts">
    import type { Snippet } from "svelte";

    let {
        open = $bindable(false),
        title,
        width = 480,
        onsubmit,
        submitText = "Save",
        danger = false,
        busy = false,
        error = null,
        children,
        extra,
    }: {
        open?: boolean;
        title: string;
        width?: number;
        onsubmit?: () => void | Promise<void>;
        submitText?: string;
        danger?: boolean;
        busy?: boolean;
        error?: string | null;
        children: Snippet;
        extra?: Snippet;
    } = $props();

    let el: HTMLDialogElement | undefined = $state();

    $effect(() => {
        if (!el) return;
        if (open && !el.open) {
            el.showModal();
            // Focus the first field, not the close button.
            queueMicrotask(() => el?.querySelector<HTMLElement>(".dbody input, .dbody select, .dbody textarea")?.focus());
        } else if (!open && el.open) el.close();
    });

    function submit(e: SubmitEvent) {
        e.preventDefault();
        void onsubmit?.();
    }
</script>

<dialog bind:this={el} onclose={() => (open = false)} style="width: min({width}px, calc(100vw - 16px))">
    {#if open}
        <form onsubmit={submit} class="panel">
            <header>
                <h1>{title}</h1>
                <span class="spacer"></span>
                <button type="button" class="ghost" onclick={() => (open = false)} aria-label="Close">×</button>
            </header>
            <div class="body col dbody">
                {#if error}<div class="notice bad">{error}</div>{/if}
                {@render children()}
            </div>
            <footer class="row">
                {@render extra?.()}
                <span class="spacer"></span>
                <button type="button" onclick={() => (open = false)}>Cancel</button>
                {#if onsubmit}
                    <button type="submit" class={danger ? "danger" : "primary"} disabled={busy}>{submitText}</button>
                {/if}
            </footer>
        </form>
    {/if}
</dialog>

<style>
    dialog {
        padding: 0;
        border: 1px solid var(--line-strong);
        background: var(--panel);
        color: var(--text);
        max-height: calc(100vh - 32px);
        box-shadow: 0 6px 24px rgb(0 0 0 / 0.25);
    }
    dialog::backdrop {
        background: rgb(0 0 0 / 0.35);
    }
    form {
        border: 0;
    }
    footer {
        padding: 8px 10px;
        border-top: 1px solid var(--line);
        background: var(--panel-2);
    }
</style>
