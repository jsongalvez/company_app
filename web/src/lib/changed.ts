import type { Action } from "svelte/action";

/** Flash a value that changed while on screen. Not on first render, not from "not loaded yet", not with reduced motion. */
export const changed: Action<HTMLElement, unknown> = (node, value) => {
    let last = value;
    node.addEventListener("animationend", () => node.classList.remove("changed"));
    return {
        update(next) {
            const was = last;
            last = next;
            if (was === undefined || was === next || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
            node.classList.remove("changed");
            void node.offsetWidth;
            node.classList.add("changed");
        },
    };
};
