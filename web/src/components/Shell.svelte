<script lang="ts">
    import {
        app,
        can,
        canAnywhere,
        canBranchWide,
        canEverywhere,
        selectBranch,
        signOut,
        clockIn,
        clockOut,
        stopViewAs,
        setViewAsChanges,
    } from "../lib/app.svelte";
    import { route, go } from "../lib/router.svelte";
    import ThemeIcon from "./ThemeIcon.svelte";
    import FullIcon from "./FullIcon.svelte";
    import InstallApp from "./InstallApp.svelte";
    import Feedback from "./Feedback.svelte";
    import { load, warm, type Loader } from "../lib/data";
    import { peek } from "../lib/cache.svelte";
    import NetBar from "./NetBar.svelte";
    import Lazy from "./Lazy.svelte";
    import { page, type PageName } from "../lib/pages.svelte";
    import { day, label, operationalDate, monthStart, clientName } from "../lib/format";
    import { searchLocal } from "../lib/clientdir.svelte";
    import Today from "../pages/Today.svelte";

    /** `page`: the screen's code, and `reads`: what it shows first. The menu opening fetches both (reads only if never saved); a touch refetches the reads. */
    type Item = { path: string; text: string; show: boolean; page?: PageName; reads?: () => Loader<unknown>[] };

    const bid = $derived(app.branchId);
    const view = $derived(can("VIEW_BRANCH_DATA"));
    // Reports cover the whole branch, so relief's one-day grant doesn't open them. Relief does see the stock.
    const viewBranch = $derived(canBranchWide("VIEW_BRANCH_DATA"));
    // Today's Expenses & pay and Sales reports, as those pages read them when opened with no date.
    function financeReads(): Loader<unknown>[] {
        const d = operationalDate();
        const dayId = app.today?.branchDayId;
        if (!dayId) return [load.branchDay(bid, d)];
        const pay = can("ASSIGN_COMPENSATION");
        return [
            load.daySessions(bid, d),
            load.expenses(dayId),
            ...(can("VIEW_COMMISSION", bid, dayId) ? [load.commissionSplits(dayId)] : []),
            ...(pay ? [load.compensations(dayId), load.allowances(dayId), load.dayUsers(dayId)] : []),
            load.dailySummary(bid, d),
            load.members(bid),
        ];
    }
    function reportReads(): Loader<unknown>[] {
        const d = operationalDate();
        const from = monthStart(d);
        return [load.dailySummaries(bid, from, d), load.monthly(bid, Number(from.slice(0, 4)), Number(from.slice(5, 7)))];
    }
    const nav = $derived<{ title: string; items: Item[] }[]>([
        {
            title: "Branch",
            items: [
                { path: "/today", text: "Sessions", show: view, reads: () => [load.dashboard(bid), load.roster(bid)] },
                { path: "/clients", page: "Clients", text: "Clients", show: view || canAnywhere("EDIT_BRANCH_DATA") },
                { path: "/inventory", page: "Inventory", text: "Stock", show: view, reads: () => [load.inventory(bid), load.movements(bid, "")] },
                { path: "/finance", page: "Finance", text: "Expenses & pay", show: view, reads: financeReads },
                { path: "/reports", page: "Reports", text: "Sales reports", show: viewBranch, reads: reportReads },
                {
                    path: "/remittance",
                    page: "Remittances",
                    text: "Remittance",
                    show: can("SUBMIT_REMITTANCE") || canEverywhere("VIEW_BRANCH_DATA"),
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
                { path: "/users", page: "Users", text: "Users", show: canAnywhere("MANAGE_USERS"), reads: () => [load.users()] },
                {
                    path: "/branches",
                    page: "Branches",
                    text: "Branches",
                    show: canAnywhere("MANAGE_USERS") || canAnywhere("ASSIGN_DELEGATE"),
                    reads: () => [load.branches()],
                },
                // Owed #25(a): who pays at every branch pays from one page.
                { path: "/pay", page: "Payouts", text: "Pay, all branches", show: canEverywhere("ASSIGN_COMPENSATION") },
                { path: "/catalog", page: "Catalog", text: "Products", show: canAnywhere("MANAGE_CATALOG"), reads: () => [load.products()] },
                { path: "/feedback", page: "FeedbackList", text: "Feedback inbox", show: canAnywhere("MANAGE_USERS") },
                { path: "/audit", page: "Audit", text: "Change history", show: true, reads: () => [load.auditTables()] },
            ],
        },
    ]);

    const section = $derived("/" + (route.current.parts[0] ?? ""));
    // The page Alerts was opened from. On Alerts, Home keeps its space (hidden) when that page
    // showed it, so the Alerts button doesn't jump between taps.
    let before = $state("/today");
    $effect(() => {
        if (section !== "/notifications") before = section;
    });
    const homeSpace = $derived(section === "/notifications" && before !== "/today");
    let menuOpen = $state(false);
    // People take ~0.9s from opening the menu to tapping a page; a page with nothing saved for today
    // (first Sales reports or Expenses & pay of the day) gets its data in that time instead of "Loading…".
    $effect(() => {
        if (!menuOpen) return;
        for (const group of nav)
            for (const item of group.items)
                if (item.show) {
                    if (item.page) page(item.page);
                    item.reads?.()
                        .filter((l) => peek(l.key) === undefined)
                        .forEach(warm);
                }
    });
    // The phone menu hangs from the bottom of the top bar, wherever that is (the "Viewing as" bar pushes it down).
    let topEl: HTMLElement | undefined = $state();
    let topH = $state(76);
    $effect(() => {
        if (menuOpen && topEl) {
            void app.viewAs;
            topH = Math.round(topEl.getBoundingClientRect().bottom);
        }
    });
    // Lock the page under the open menu (iPhone otherwise scrolls the page beneath it).
    $effect(() => {
        document.documentElement.classList.toggle("menu-open", menuOpen);
        return () => document.documentElement.classList.remove("menu-open");
    });
    let feedbackOpen = $state(false);
    $effect(() => {
        void route.current;
        menuOpen = false;
    });

    // Alerts works as a toggle: tapping it again goes back where you were.
    function toggleAlerts(e: MouseEvent) {
        if (section !== "/notifications") return;
        e.preventDefault();
        if (history.length > 1) history.back();
        else location.hash = "#/today";
    }

    // Branch picker: home/relief branches first, then all-branch read access.
    const branchOptions = $derived.by(() => {
        const mine = app.branches.map((b) => ({ id: b.branchId, name: b.branchName, note: b.isRelief ? " (relief)" : "" }));
        const seen = new Set(mine.map((b) => b.id));
        const rest = app.accessible.filter((b) => !seen.has(b.id)).map((b) => ({ id: b.id, name: b.name, note: "" }));
        return { mine, rest };
    });

    const here = $derived(app.branches.find((b) => b.branchId === bid));
    const clockedHere = $derived(app.shift?.branchId === bid);
    const clockedElsewhere = $derived(!!app.shift && app.shift.branchId !== bid);
    let clockBusy = $state(false);

    async function doClockIn() {
        clockBusy = true;
        await clockIn(bid);
        clockBusy = false;
    }
    async function doClockOut() {
        clockBusy = true;
        await clockOut();
        clockBusy = false;
    }

    let q = $state("");
    let qFocus = $state(false);
    // Suggestions come from the client list kept on the phone, so they show at once, offline too.
    const hints = $derived(qFocus && q.trim().length >= 2 ? searchLocal(q, 6) : []);
    function search(e: SubmitEvent) {
        e.preventDefault();
        qFocus = false;
        go("/clients", { q: q.trim() });
    }
    function pick(id: string) {
        qFocus = false;
        q = "";
        menuOpen = false;
        go(`/clients/${id}`);
    }

    // Default landing page.
    $effect(() => {
        if (route.current.parts.length === 0) go(view ? "/today" : "/relief");
    });

    const opDate = $derived(operationalDate());
</script>

<NetBar />
<div class="shell" class:viewing={!!app.viewAs}>
    {#if app.viewAs}
        <div class="viewas" role="status">
            <span class="ellipsis"
                >Viewing as <b>{app.viewAs.name}</b>{#if app.viewAs.roles.length}&nbsp;({app.viewAs.roles.map(label).join(", ")}){/if}</span
            >
            <label class="check nowrap"
                ><input type="checkbox" checked={app.viewAs.allowChanges} onchange={(e) => setViewAsChanges(e.currentTarget.checked)} />Allow changes</label
            >
            <button class="sm" onclick={() => stopViewAs()}>Back to me</button>
        </div>
    {/if}
    <header class="top" bind:this={topEl}>
        <button class="ghost menu" onclick={() => (menuOpen = !menuOpen)} aria-label="Menu" aria-expanded={menuOpen}>☰</button>
        <a class="brand" href="#/today">CompanyApp</a>
        {#if homeSpace && view}<span class="btn home placeholder" aria-hidden="true">⌂ Home</span
            >{:else if section !== "/today" && section !== "/notifications" && view}<a
                class="btn home"
                href="#/today"
                onclick={() => (menuOpen = false)}
                title="Home: back to Sessions"
                aria-label="Home">⌂ Home</a
            >{/if}

        <form class="search" onsubmit={search} role="search">
            <input
                type="search"
                placeholder="Find client by name or phone"
                bind:value={q}
                aria-label="Find client"
                autocomplete="off"
                onfocus={() => (qFocus = true)}
                onblur={() => setTimeout(() => (qFocus = false), 150)}
                onkeydown={(e) => e.key === "Escape" && (qFocus = false)}
            />
            <button type="submit">Find</button>
            {#if hints.length}
                <ul class="hints" role="listbox" aria-label="Matching clients">
                    {#each hints as c (c.id)}
                        <li role="option" aria-selected="false">
                            <button type="button" onmousedown={(e) => e.preventDefault()} onclick={() => pick(c.id)}
                                ><span class="ellipsis">{clientName(c)}</span><span class="faint"
                                    >{c.gender === "UNKNOWN" ? "" : c.gender} {c.age ?? ""}{c.phoneNumber ? ` · ${c.phoneNumber}` : ""}</span
                                ></button
                            >
                        </li>
                    {/each}
                </ul>
            {/if}
        </form>

        <select class="branch" value={bid} onchange={(e) => selectBranch(e.currentTarget.value)} aria-label="Branch">
            {#if !bid}<option value="">No branch</option>{/if}
            {#if branchOptions.mine.length}
                <optgroup label="My branches">
                    {#each branchOptions.mine as b (b.id)}<option value={b.id}>{b.name}{b.note}</option>{/each}
                </optgroup>
            {/if}
            {#if branchOptions.rest.length}
                <optgroup label="All branches">
                    {#each branchOptions.rest as b (b.id)}<option value={b.id}>{b.name}</option>{/each}
                </optgroup>
            {/if}
        </select>

        <span class="day nowrap" title="Business day. It ends at 4 AM.">
            {day(opDate)}
            {#if app.today?.status && app.today.status !== "OPEN"}
                <span class="mark {app.today.status === 'REMITTED' ? 'info' : 'plain'}">{label(app.today.status)}</span>
            {/if}
        </span>

        <span class="spacer"></span>

        {#if clockedHere}
            <button class="clock ok" onclick={doClockOut} disabled={clockBusy} title="On duty here{app.shift?.isRelief ? ' (relief)' : ''}">Clock out</button>
        {:else if clockedElsewhere}
            <button class="clock" onclick={doClockOut} disabled={clockBusy} title="Clock out of {app.shift?.branchName}"
                >Clock out<span class="hide-sm">&nbsp;of {app.shift?.branchName}</span></button
            >
        {:else if bid && here && app.shiftKnown}
            <button class="primary" onclick={doClockIn} disabled={clockBusy}>Clock in</button>
        {/if}

        <a
            class="btn"
            class:active={section === "/notifications"}
            href="#/notifications"
            title={section === "/notifications" ? "Back to the previous page" : app.unread ? `${app.unread} unread` : "Alerts"}
            aria-label={section === "/notifications" ? "Close alerts" : "Alerts"}
            onclick={toggleAlerts}
            onmouseenter={() => warm(load.notifications())}
        >
            Alerts{#if app.unread}<b class="count">{app.unread}</b>{/if}
        </a>
        <button class="hide-sm" onclick={() => (feedbackOpen = true)}>Feedback</button>
        <ThemeIcon />
        <FullIcon />
        <a class="user muted ellipsis" href="#/profile" title="My profile ({app.me?.username})">{app.me?.displayName}</a>
        <button class="ghost" onclick={() => signOut()}>Sign out</button>
    </header>

    <nav class:open={menuOpen} aria-label="Main" style:--top-h="{topH}px">
        {#each nav as group (group.title)}
            {#if group.items.some((i) => i.show)}
                <h3>{group.title}</h3>
                {#each group.items.filter((i) => i.show) as item (item.path)}
                    <a
                        href="#{item.path}"
                        class:active={section === item.path}
                        onclick={() => (menuOpen = false)}
                        onmouseenter={() => item.reads?.().forEach(warm)}
                        onpointerdown={() => item.reads?.().forEach(warm)}
                        onfocus={() => item.reads?.().forEach(warm)}>{item.text}</a
                    >
                {/each}
            {/if}
        {/each}
        <InstallApp />
        <div class="navfoot faint">
            <button class="ghost sm" onclick={() => (feedbackOpen = true)}>Send feedback</button>
            <button class="ghost sm" onclick={() => signOut()}>Sign out</button>
        </div>
    </nav>

    <Feedback bind:open={feedbackOpen} />

    <main>
        {#if !bid && ["/today", "/inventory", "/finance", "/reports", "/remittance"].includes(section)}
            <div class="notice">You aren't assigned to a branch yet. Ask a manager to assign you, or request <a href="#/relief">relief duty</a>.</div>
        {:else if section === "/today"}
            <Today />
        {:else if section === "/clients" && route.current.parts[1]}
            <Lazy name="ClientDetail" id={route.current.parts[1]} />
        {:else if section === "/clients"}
            <Lazy name="Clients" />
        {:else if section === "/sessions" && route.current.parts[1]}
            <Lazy name="SessionDetail" id={route.current.parts[1]} />
        {:else if section === "/inventory"}
            <Lazy name="Inventory" />
        {:else if section === "/finance"}
            <Lazy name="Finance" />
        {:else if section === "/pay"}
            <Lazy name="Payouts" />
        {:else if section === "/reports"}
            <Lazy name="Reports" />
        {:else if section === "/remittance" && route.current.parts[1]}
            <Lazy name="RemittanceDetail" id={route.current.parts[1]} />
        {:else if section === "/remittance"}
            <Lazy name="Remittances" />
        {:else if section === "/relief"}
            <Lazy name="Relief" />
        {:else if section === "/notifications"}
            <Lazy name="Notifications" />
        {:else if section === "/users"}
            <Lazy name="Users" />
        {:else if section === "/branches"}
            <Lazy name="Branches" />
        {:else if section === "/catalog"}
            <Lazy name="Catalog" />
        {:else if section === "/feedback"}
            <Lazy name="FeedbackList" />
        {:else if section === "/profile"}
            <Lazy name="Profile" />
        {:else if section === "/my-history"}
            <Lazy name="MyHistory" />
        {:else if section === "/audit"}
            <Lazy name="Audit" />
        {:else}
            <div class="notice">Page not found. <a href="#/today">Go to Sessions</a></div>
        {/if}
    </main>
</div>

<style>
    .home.placeholder {
        visibility: hidden;
        pointer-events: none;
    }
    .shell {
        display: grid;
        grid-template-columns: 168px 1fr;
        grid-template-rows: 40px 1fr;
        height: 100%;
    }
    .shell.viewing {
        grid-template-rows: auto 40px 1fr;
    }
    .viewas {
        grid-column: 1 / -1;
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 4px 10px;
        background: var(--warn);
        color: var(--panel);
        font-size: 13px;
        min-width: 0;
    }
    .viewas > span {
        flex: 1;
        min-width: 0;
    }
    .viewas .check {
        color: inherit;
    }
    .top {
        grid-column: 1 / -1;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 0 10px;
        background: var(--panel);
        border-bottom: 1px solid var(--line-strong);
        min-width: 0;
    }
    .brand {
        font-weight: 700;
        color: var(--text);
        margin-right: 6px;
        white-space: nowrap;
    }
    .search {
        display: flex;
        width: 300px;
        min-width: 140px;
    }
    .search input {
        flex: 1;
    }
    .search {
        position: relative;
    }
    .hints {
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        z-index: 60;
        margin: 2px 0 0;
        padding: 0;
        list-style: none;
        background: var(--panel);
        border: 1px solid var(--line-strong);
        box-shadow: 0 4px 12px rgb(0 0 0 / 0.15);
    }
    .hints button {
        display: flex;
        width: 100%;
        gap: 8px;
        justify-content: space-between;
        border: 0;
        border-bottom: 1px solid var(--line);
        border-radius: 0;
        background: none;
        text-align: left;
        height: auto;
        padding: 8px;
        min-width: 0;
    }
    .hints button:hover,
    .hints button:focus {
        background: var(--hover, var(--line));
    }
    .search button {
        margin-left: -1px;
    }
    .branch {
        max-width: 200px;
    }
    .day {
        display: inline-flex;
        gap: 6px;
        align-items: center;
        color: var(--text-2);
    }
    .user {
        max-width: 140px;
    }
    .count {
        background: var(--bad);
        color: #fff;
        padding: 0 4px;
        font-size: 11px;
        line-height: 16px;
    }
    .menu {
        display: none;
        font-size: 16px;
    }
    nav {
        background: var(--panel);
        border-right: 1px solid var(--line);
        overflow: auto;
        padding: 6px 0;
        display: flex;
        flex-direction: column;
    }
    nav h3 {
        padding: 10px 12px 4px;
    }
    nav a {
        display: block;
        padding: 5px 12px;
        color: var(--text);
        border-left: 3px solid transparent;
    }
    nav a:hover {
        background: var(--row-hover);
        text-decoration: none;
    }
    a.btn.active {
        background: var(--accent-soft);
        color: var(--accent);
        border-color: var(--accent);
    }
    nav a.active {
        border-left-color: var(--accent);
        background: var(--accent-soft);
        color: var(--accent);
        font-weight: 600;
    }
    .navfoot {
        display: none;
    }
    main {
        overflow: auto;
        padding: 12px;
        min-width: 0;
    }

    /* Tablet: drop secondary top-bar items. */
    @media (max-width: 1100px) {
        .user,
        .top > button.ghost:last-child {
            display: none;
        }
        .search {
            width: 200px;
        }
    }

    /* Phone: one column, nav becomes a drop-down list under the bar. */
    @media (max-width: 760px) {
        .shell {
            grid-template-columns: 1fr;
            grid-template-rows: auto 1fr;
        }
        .shell.viewing {
            grid-template-rows: auto auto 1fr;
        }
        .viewas {
            flex-wrap: wrap;
            row-gap: 2px;
        }
        .viewas > span {
            flex-basis: 100%;
        }
        .top {
            flex-wrap: wrap;
            padding: 6px 8px;
            gap: 6px;
        }
        .menu {
            display: inline-flex;
        }
        .search {
            order: 10;
            flex: 1 1 220px;
            width: auto;
            min-width: 0;
        }
        .branch {
            max-width: none;
            flex: 1;
            min-width: 0;
            width: 0;
        }
        .brand {
            display: none;
        }
        /* Phones: Home sits at the start of the search row, not in the full top row. */
        .home {
            flex: none;
            order: 9;
        }
        .day,
        .top > button:not(.menu):not(.primary),
        .top > .spacer {
            display: none;
        }
        .top > button.primary,
        .top > button.clock:not(.menu),
        .top > :global(button.full) {
            display: inline-flex;
        }
        nav {
            display: none;
            position: fixed;
            inset: auto 0 0 0;
            top: var(--top-h, 76px);
            z-index: 50;
            border-right: 0;
            border-top: 1px solid var(--line-strong);
        }
        nav.open {
            display: flex;
            overflow-y: auto;
            overscroll-behavior: contain;
            -webkit-overflow-scrolling: touch;
        }
        nav a {
            padding: 10px 14px;
        }
        .navfoot {
            display: flex;
            gap: 8px;
            padding: 12px 10px;
            margin-top: auto;
        }
        main {
            padding: 8px;
        }
    }
</style>
