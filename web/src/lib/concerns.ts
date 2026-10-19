import type { S } from "../api/client";

/** The most common concerns, shown first and in this order; the rest follow A to Z. */
export const COMMON_CONCERNS = ["Knee pain", "Shoulder pain", "Lower back pain", "Back pain", "TMJ", "Vertigo", "Hearing impaired"];

const rank = (label: string) => {
    const i = COMMON_CONCERNS.findIndex((c) => c.toLowerCase() === label.toLowerCase());
    return i < 0 ? COMMON_CONCERNS.length : i;
};

export function sortConcerns<T extends Pick<S["ConcernResponse"], "label">>(list: T[]): T[] {
    return [...list].sort((a, b) => rank(a.label) - rank(b.label) || a.label.localeCompare(b.label));
}

/** Split typed "other" concerns ("neck, wrist pain") into tidy names. */
export function typedConcerns(text: string): string[] {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const raw of text.split(/[,;\n]/)) {
        const t = raw.trim().replace(/\s+/g, " ");
        if (!t || seen.has(t.toLowerCase())) continue;
        seen.add(t.toLowerCase());
        out.push(t.charAt(0).toUpperCase() + t.slice(1));
    }
    return out;
}
