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

    // WCAG contrast of each text against the colour behind it: 4.5:1, or 3:1 for large text. A canvas pixel turns any
    // CSS colour into sRGB. Disabled controls are exempt; opacity is not counted; a background image gives no verdict.
    const pen = document.createElement("canvas").getContext("2d", { willReadFrequently: true })!;
    const rgba = (colour: string): number[] => {
        pen.clearRect(0, 0, 1, 1);
        pen.fillStyle = colour;
        pen.fillRect(0, 0, 1, 1);
        const [r, g, b, a] = pen.getImageData(0, 0, 1, 1).data;
        return [r, g, b, a / 255];
    };
    const over = (top: number[], under: number[]) => [0, 1, 2].map((i) => top[i] * top[3] + under[i] * (1 - top[3])).concat(1);
    const backs = new Map<Element, number[] | null>();
    const behind = (el: Element | null): number[] | null => {
        if (!el) return [255, 255, 255, 1];
        if (!backs.has(el)) {
            const s = getComputedStyle(el);
            const own = rgba(s.backgroundColor);
            if (s.backgroundImage !== "none") backs.set(el, null);
            else if (own[3] >= 1) backs.set(el, own);
            else {
                const below = behind(el.parentElement);
                backs.set(el, below && over(own, below));
            }
        }
        return backs.get(el) ?? null;
    };
    const lum = (c: number[]) => {
        const [r, g, b] = c.slice(0, 3).map((v) => (v / 255 <= 0.03928 ? v / 255 / 12.92 : ((v / 255 + 0.055) / 1.055) ** 2.4));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    };
    for (const el of texts) {
        if (!shown(el) || el.closest(":disabled, [aria-disabled=true], svg")) continue;
        const back = behind(el);
        if (!back) continue;
        const s = getComputedStyle(el);
        const [hi, lo] = [lum(over(rgba(s.color), back)), lum(back)].sort((a, b) => b - a);
        const ratio = (hi + 0.05) / (lo + 0.05);
        const need = px(el) >= 24 || (px(el) >= 18.66 && Number(s.fontWeight) >= 700) ? 3 : 4.5;
        if (ratio < need) out.push(`text contrast ${ratio.toFixed(2)} to 1, under ${need} to 1: ${name(el)}`);
    }

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

    // Items on one line overlap in height however they are aligned; one that starts below another's bottom is on a new line.
    for (const bar of document.querySelectorAll("[data-one-row]")) {
        if (!shown(bar)) continue;
        const boxes = [...bar.children].filter(shown).map((c) => c.getBoundingClientRect());
        if (boxes.some((a) => boxes.some((b) => b.top >= a.bottom - 1))) out.push(`bar wraps to a second row: ${name(bar)}`);
    }
    return out;
}
