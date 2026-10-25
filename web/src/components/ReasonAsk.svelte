<script lang="ts">
    import { ask } from "../lib/ask.svelte";
    import Dialog from "./Dialog.svelte";

    $effect(() => {
        if (!ask.open) ask.done?.(null);
    });
</script>

<Dialog bind:open={ask.open} title="Reason for change" width={400} busy={!ask.text.trim()} onsubmit={() => ask.done?.(ask.text.trim())}>
    <div class="muted">{ask.title}</div>
    <label class="field wide"><span class="req">Reason</span><input bind:value={ask.text} required /></label>
</Dialog>
