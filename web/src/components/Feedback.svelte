<script lang="ts">
    import Dialog from "./Dialog.svelte";
    import { api, ok, errText, uuid, lastError } from "../api/client";
    import { toast } from "../lib/app.svelte";

    let { open = $bindable(false) }: { open?: boolean } = $props();

    let kind = $state<"PROBLEM" | "IDEA">("PROBLEM");
    let message = $state("");
    let attach = $state(true);
    let busy = $state(false);
    let error = $state("");
    let id = uuid();

    const recent = $derived(lastError.value);
    const recentText = $derived(recent ? `Last refused request: ${recent.method} ${recent.path} → ${recent.status} at ${recent.at}` : "");

    $effect(() => {
        if (open) {
            error = "";
            id = uuid();
        }
    });

    async function send() {
        busy = true;
        error = "";
        try {
            const details = [`Screen: ${innerWidth}×${innerHeight}`, `Browser: ${navigator.userAgent}`];
            if (kind === "PROBLEM" && attach && recentText) details.unshift(recentText);
            await ok(
                api.POST("/api/feedback-notes", {
                    body: { id, kind, message, page: location.hash || "#/", details: details.join("\n"), appVersion: __APP_VERSION__ },
                }),
            );
            toast(kind === "PROBLEM" ? "Problem sent. Thank you." : "Idea sent. Thank you.");
            message = "";
            open = false;
        } catch (e) {
            error = errText(e);
        } finally {
            busy = false;
        }
    }
</script>

<Dialog bind:open title="Send feedback" submitText="Send" onsubmit={send} {busy} {error}>
    <div class="seg" role="group" aria-label="Kind">
        <button type="button" class:on={kind === "PROBLEM"} onclick={() => (kind = "PROBLEM")}>Something's wrong</button>
        <button type="button" class:on={kind === "IDEA"} onclick={() => (kind = "IDEA")}>Idea or request</button>
    </div>
    <label class="field">
        <span class="req">{kind === "PROBLEM" ? "What happened, and what did you expect?" : "What would help?"}</span>
        <textarea bind:value={message} rows="5" maxlength="4000" required></textarea>
    </label>
    {#if kind === "PROBLEM" && recentText}
        <label class="check"><input type="checkbox" bind:checked={attach} /> Include the last error ({recent?.status} on {recent?.path})</label>
    {/if}
</Dialog>
