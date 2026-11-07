// What every screen must hold to on a 392x718 phone. The floors are the smallest tokens in
// web/src/ui/tokens.css: a token that shrinks below them, or a screen that skips the tokens, fails here.
export const PHONE = { width: 392, height: 718 } as const;
export const FLOORS = { text: 13, body: 15, input: 16, tap: 44 } as const;
export type Floors = { text: number; body: number; input: number; tap: number };

/** Runs inside the page (browser.evaluate): one line per problem, empty when the screen fits a phone. */
export function phoneProblems(floors: Floors): string[] {
    const out: string[] = [];
    const shown = (el: Element): boolean => {
        const s = getComputedStyle(el);
        if (s.display === "none" || s.visibility === "hidden" || el.closest("[hidden],[inert]")) return false;
        const r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0;
    };
    const name = (el: Element): string => {
        const text = (el.getAttribute("aria-label") ?? el.textContent ?? "").replace(/\s+/g, " ").trim().slice(0, 40);
        return `<${el.tagName.toLowerCase()}${el.className && typeof el.className === "string" ? "." + el.className.trim().split(/\s+/).join(".") : ""}> "${text}"`;
    };
    const px = (el: Element) => parseFloat(getComputedStyle(el).fontSize);

    const wide = document.documentElement.scrollWidth;
    if (wide > window.innerWidth + 1) out.push(`scrolls sideways: ${wide}px wide on a ${window.innerWidth}px screen`);
    if (px(document.body) < floors.body) out.push(`body text ${px(document.body)}px, under ${floors.body}px`);

    const texts = new Set<Element>();
    const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let n = walk.nextNode(); n; n = walk.nextNode()) if (n.parentElement && n.textContent?.trim()) texts.add(n.parentElement);
    for (const el of texts) if (shown(el) && px(el) < floors.text) out.push(`text ${px(el)}px, under ${floors.text}px: ${name(el)}`);

    for (const el of document.querySelectorAll("input:not([type=hidden]):not([type=checkbox]):not([type=radio]), select, textarea"))
        if (shown(el) && px(el) < floors.input) out.push(`input text ${px(el)}px, under ${floors.input}px (the phone zooms in): ${name(el)}`);

    for (const el of document.querySelectorAll(
        "button, a[href], input:not([type=hidden]), select, textarea, summary, [role=button], [role=link], [role=tab]",
    )) {
        if (!shown(el)) continue;
        const box = (el.matches("input[type=checkbox], input[type=radio]") ? el.closest("label") : null) ?? el;
        const r = box.getBoundingClientRect();
        if (r.width < floors.tap || r.height < floors.tap)
            out.push(`tap target ${Math.round(r.width)}x${Math.round(r.height)}, under ${floors.tap}px: ${name(el)}`);
    }

    for (const bar of document.querySelectorAll("[data-one-row]")) {
        if (!shown(bar)) continue;
        const tops = [...bar.children].filter(shown).map((c) => Math.round(c.getBoundingClientRect().top));
        if (tops.length && Math.max(...tops) - Math.min(...tops) > 4) out.push(`bar wraps to a second row: ${name(bar)}`);
    }
    return out;
}
