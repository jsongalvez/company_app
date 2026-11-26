// The menu: its groups and pages, and what each page reads first.
import { app, can, canRead } from "./app.svelte";
import { today } from "./today.svelte";
import { load, type Loader } from "./data";
import type { PageName } from "./pages.svelte";
import { operationalDate, monthStart } from "./format";

/** `page`: the screen's code, and `reads`: what it shows first. The menu opening fetches both (reads only if never saved); a touch refetches the reads. */
export type Item = { path: string; text: string; show: boolean; page?: PageName; reads?: () => Loader<unknown>[] };

export type NavGroup = { title: string; items: Item[] };

// Today's Expenses & pay and Sales reports, as those pages read them when opened with no date.
function financeReads(bid: string): Loader<unknown>[] {
    const d = operationalDate();
    const dayId = today.day?.branchDayId;
    if (!dayId) return [load.branchDay(bid, d)];
    const pay = can("PAY", { branch: bid, day: dayId });
    return [
        load.daySessions(bid, d),
        load.expenses(dayId),
        ...(canRead("SEE_COMMISSION", { branch: bid, day: dayId }) ? [load.commissionSplits(dayId)] : []),
        ...(pay ? [load.compensations(dayId), load.allowances(dayId), load.dayUsers(dayId)] : []),
        load.dailySummary(bid, d),
        load.members(bid),
    ];
}
function reportReads(bid: string): Loader<unknown>[] {
    const d = operationalDate();
    const from = monthStart(d);
    return [load.dailySummaries(bid, from, d), load.monthly(bid, Number(from.slice(0, 4)), Number(from.slice(5, 7)))];
}

/** Read inside a `$derived`, so the menu follows the rights and the branch. */
export function navGroups(bid: string): NavGroup[] {
    const view = canRead("SEE_DAY");
    // Reports cover the whole branch, so relief's one-day grant doesn't open them. Relief does see the stock.
    const viewBranch = canRead("SEE_BRANCH", { branch: bid });
    return [
        {
            title: "Branch",
            items: [
                { path: "/today", text: "Sessions", show: view, reads: () => [load.dashboard(bid), load.roster(bid)] },
                { path: "/clients", page: "Clients", text: "Clients", show: canRead("WORK_ANYWHERE") },
                { path: "/inventory", page: "Inventory", text: "Stock", show: view, reads: () => [load.inventory(bid), load.movements(bid, "")] },
                { path: "/finance", page: "Finance", text: "Expenses & pay", show: view, reads: () => financeReads(bid) },
                { path: "/reports", page: "Reports", text: "Sales reports", show: viewBranch, reads: () => reportReads(bid) },
                {
                    path: "/remittance",
                    page: "Remittances",
                    text: "Remittance",
                    show: canRead("SUBMIT_REMITTANCE", { branch: bid }),
                    reads: () => [load.remittances(bid)],
                },
            ],
        },
        {
            title: "Me",
            items: [
                { path: "/profile", page: "Profile", text: "My profile", show: true },
                { path: "/my-history", page: "MyHistory", text: "My shifts & pay", show: true },
                { path: "/relief", page: "Relief", text: "Relief duty", show: true, reads: () => [load.reliefMine(), load.reliefInvitesMine()] },
                {
                    path: "/notifications",
                    page: "Notifications",
                    text: app.unread ? `Alerts (${app.unread})` : "Alerts",
                    show: true,
                    reads: () => [load.notifications()],
                },
            ],
        },
        {
            title: "Admin",
            items: [
                { path: "/users", page: "Users", text: "Users", show: canRead("MANAGE_USERS"), reads: () => [load.users()] },
                {
                    path: "/branches",
                    page: "Branches",
                    text: "Branches",
                    show: can("MANAGE_USERS") || can("ASSIGN_DELEGATE") || app.accessible.length > 0,
                    reads: () => (canRead("MANAGE_USERS") ? [load.branches()] : []),
                },
                // Who pays at every branch pays from one page.
                { path: "/pay", page: "Payouts", text: "Pay, all branches", show: can("PAY", {}) },
                { path: "/catalog", page: "Catalog", text: "Products", show: canRead("MANAGE_CATALOG"), reads: () => [load.products()] },
                { path: "/feedback", page: "FeedbackList", text: "Feedback inbox", show: canRead("MANAGE_USERS") },
                { path: "/audit", page: "Audit", text: "Change history", show: true, reads: () => [load.auditTables()] },
            ],
        },
    ];
}
