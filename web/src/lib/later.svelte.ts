import type { Component } from "svelte";
import { importOrReload } from "./shell";

// A dialog opened only by a tap ships as its own file, so Today's first open doesn't wait for it.
// The service worker saves that file with the rest, so the dialog still opens with no signal.
export function later<T extends Component<any, any, any>>(file: () => Promise<{ default: T }>) {
    const s = $state<{ c?: T }>({});
    let loading: Promise<unknown> | undefined;
    return {
        /** The component once its file is in. */
        get c() {
            return s.c;
        },
        /** Starts fetching the file, once. */
        load() {
            loading ??= importOrReload(file)
                .then((m) => (s.c = m.default))
                .catch(() => (loading = undefined));
        },
    };
}
