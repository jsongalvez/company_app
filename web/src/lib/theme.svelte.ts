export type Theme = "light" | "dark";

export const theme = $state<{ value: Theme }>({
    value: (document.documentElement.dataset.theme as Theme) ?? "light",
});

export function setTheme(t: Theme) {
    theme.value = t;
    document.documentElement.dataset.theme = t;
    localStorage.setItem("theme", t);
}

export function toggleTheme() {
    setTheme(theme.value === "dark" ? "light" : "dark");
}
