import { errText } from "../api/client";
import { toast } from "./app.svelte";

/** Form/action runner: busy flag, inline error, success toast. */
export class Action {
    busy = $state(false);
    error = $state<string | null>(null);

    async run(fn: () => Promise<unknown>, done?: string): Promise<boolean> {
        this.busy = true;
        this.error = null;
        try {
            await fn();
            if (done) toast(done);
            return true;
        } catch (e) {
            this.error = errText(e);
            return false;
        } finally {
            this.busy = false;
        }
    }

    reset() {
        this.error = null;
    }
}

/** One-shot action outside a form: errors go to a toast. */
export async function act(fn: () => Promise<unknown>, done?: string): Promise<boolean> {
    try {
        await fn();
        if (done) toast(done);
        return true;
    } catch (e) {
        toast(errText(e), "bad");
        return false;
    }
}
