/** Every screen but Today, each its own file, so opening Today downloads only Today's code. The service worker saves them all in the background (public/sw.js follows the names in the scripts). */
import type { Component } from "svelte";
import { importOrReload } from "./shell";

const files = {
    Clients: () => import("../pages/Clients.svelte"),
    ClientDetail: () => import("../pages/ClientDetail.svelte"),
    SessionDetail: () => import("../pages/SessionDetail.svelte"),
    Inventory: () => import("../pages/Inventory.svelte"),
    Finance: () => import("../pages/Finance.svelte"),
    Payouts: () => import("../pages/Payouts.svelte"),
    Reports: () => import("../pages/Reports.svelte"),
    Remittances: () => import("../pages/Remittances.svelte"),
    RemittanceDetail: () => import("../pages/RemittanceDetail.svelte"),
    Relief: () => import("../pages/Relief.svelte"),
    Notifications: () => import("../pages/Notifications.svelte"),
    Users: () => import("../pages/Users.svelte"),
    Branches: () => import("../pages/Branches.svelte"),
    Catalog: () => import("../pages/Catalog.svelte"),
    FeedbackList: () => import("../pages/FeedbackList.svelte"),
    Profile: () => import("../pages/Profile.svelte"),
    MyHistory: () => import("../pages/MyHistory.svelte"),
    Audit: () => import("../pages/Audit.svelte"),
};
export type PageName = keyof typeof files;
// The screens take different props (an id or none); Lazy.svelte passes them through.
type AnyPage = Component<any>;
const imports = files as Record<PageName, () => Promise<{ default: AnyPage }>>;

const loaded = $state<Partial<Record<PageName, AnyPage>>>({});
const loading = new Set<PageName>();

/** The screen's component once its file is in; starts fetching it the first time it's asked for. */
export function page(name: PageName): AnyPage | undefined {
    if (!loaded[name] && !loading.has(name)) {
        loading.add(name);
        importOrReload(imports[name])
            .then((m) => (loaded[name] = m.default))
            .catch(() => {})
            .finally(() => loading.delete(name));
    }
    return loaded[name];
}
