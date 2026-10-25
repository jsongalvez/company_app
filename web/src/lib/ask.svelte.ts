/** Asks for the reason a closed day is being changed, at the moment it is needed. */
export const ask = $state<{ open: boolean; title: string; text: string; done: ((v: string | null) => void) | null }>({
    open: false,
    title: "",
    text: "",
    done: null,
});

// The last reason given, offered again so several fixes for one cause take one tap each.
let last = "";
export const lastReason = () => last;
export function keepReason(v: string) {
    if (v.trim()) last = v.trim();
}

/** Resolves to the typed reason, or null when the person cancels. */
export function askReason(title: string): Promise<string | null> {
    ask.done?.(null);
    return new Promise((resolve) => {
        ask.title = title;
        ask.text = last;
        ask.done = (v) => {
            ask.done = null;
            ask.open = false;
            if (v) keepReason(v);
            resolve(v);
        };
        ask.open = true;
    });
}
