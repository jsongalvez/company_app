<script lang="ts">
    import { app, can, canAnywhere, selectBranch, signOut, clockIn, clockOut } from "../lib/app.svelte";
    import { route, go } from "../lib/router.svelte";
    import ThemeIcon from "./ThemeIcon.svelte";
    import Feedback from "./Feedback.svelte";
    import FeedbackList from "../pages/FeedbackList.svelte";
    import { load, warm } from "../lib/data";
    import { invalidate } from "../lib/cache.svelte";
    import { day, label, operationalDate } from "../lib/format";
    import Today from "../pages/Today.svelte";
    import Clients from "../pages/Clients.svelte";
    import ClientDetail from "../pages/ClientDetail.svelte";
    import SessionDetail from "../pages/SessionDetail.svelte";
    import Inventory from "../pages/Inventory.svelte";
    import Finance from "../pages/Finance.svelte";
    import Reports from "../pages/Reports.svelte";
    import Remittances from "../pages/Remittances.svelte";
    import RemittanceDetail from "../pages/RemittanceDetail.svelte";
    import Relief from "../pages/Relief.svelte";
    import Notifications from "../pages/Notifications.svelte";
    import Users from "../pages/Users.svelte";
    import Branches from "../pages/Branches.svelte";
    import Catalog from "../pages/Catalog.svelte";
    import Audit from "../pages/Audit.svelte";

    type Item = { path: string; text: string; show: boolean; warm?: () => void };

    const bid = $derived(app.branchId);
    const view = $derived(can("VIEW_BRANCH_DATA"));
    const nav = $derived<{ title: string; items: Item[] }[]>([
        {
            title: "Branch",
            items: [
                { path: "/today", text: "Today", show: view, warm: () => (warm(load.dashboard(bid)), warm(load.roster(bid))) },
                { path: "/clients", text: "Clients", show: view || canAnywhere("EDIT_BRANCH_DATA") },
                { path: "/inventory", text: "Stock", show: view, warm: () => warm(load.inventory(bid)) },
                { path: "/finance", text: "Expenses & pay", show: view },
                { path: "/reports", text: "Sales reports", show: view },
                { path: "/remittance", text: "Remittance", show: can("SUBMIT_REMITTANCE"), warm: () => warm(load.remittances(bid)) },
            ],
        },
        {
            title: "Me",
            items: [
                { path: "/relief", text: "Relief duty", show: true, warm: () => (warm(load.reliefMine()), warm(load.reliefInvitesMine())) },
                {
                    path: "/notifications",
                    text: app.unread ? `Alerts (${app.unread})` : "Alerts",
                    show: true,
                    warm: () => warm(load.notifications()),
                },
            ],
        },
        {
            title: "Admin",
            items: [
                { path: "/users", text: "Users", show: canAnywhere("MANAGE_USERS"), warm: () => warm(load.users()) },
                { path: "/branches", text: "Branches", show: canAnywhere("MANAGE_USERS") || canAnywhere("ASSIGN_DELEGATE"), warm: () => warm(load.branches()) },
                { path: "/catalog", text: "Products", show: canAnywhere("MANAGE_CATALOG"), warm: () => warm(load.products()) },
                { path: "/feedback", text: "Feedback inbox", show: canAnywhere("MANAGE_USERS") },
                { path: "/audit", text: "Change history", show: true, warm: () => warm(load.auditTables()) },
            ],
        },
    ]);

    const section = $derived("/" + (route.current.parts[0] ?? ""));
    let menuOpen = $state(false);
    let feedbackOpen = $state(false);
    $effect(() => {
        void route.current;
        menuOpen = false;
    });

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
        invalidate("");
        clockBusy = false;
    }
    async function doClockOut() {
        if (!app.shift || !confirm(`Clock out of ${app.shift.branchName}?`)) return;
        clockBusy = true;
        await clockOut();
        invalidate("");
        clockBusy = false;
    }

    let q = $state("");
    function search(e: SubmitEvent) {
        e.preventDefault();
        go("/clients", { q: q.trim() });
    }

    // Default landing page.
    $effect(() => {
        if (route.current.parts.length === 0) go(view ? "/today" : "/relief");
    });

    const opDate = $derived(operationalDate());
</script>

<div class="shell">
    <header class="top">
        <button class="ghost menu" onclick={() => (menuOpen = !menuOpen)} aria-label="Menu" aria-expanded={menuOpen}>☰</button>
        <a class="brand" href="#/today">CompanyApp</a>

        <form class="search" onsubmit={search} role="search">
            <input type="search" placeholder="Find client by name or phone" bind:value={q} aria-label="Find client" />
            <button type="submit">Find</button>
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
            {#if app.today?.status}
                <span class="mark {app.today.status === 'OPEN' ? 'ok' : app.today.status === 'REMITTED' ? 'info' : 'plain'}">{label(app.today.status)}</span>
            {/if}
        </span>

        <span class="spacer"></span>

        {#if clockedHere}
            <button class="clock" onclick={doClockOut} disabled={clockBusy} title="On duty here{app.shift?.isRelief ? ' (relief)' : ''}">
                <span class="dot on"></span><span class="hide-sm">On duty ·&nbsp;</span>Clock out
            </button>
        {:else if clockedElsewhere}
            <button class="clock" onclick={doClockOut} disabled={clockBusy} title="Clock out of {app.shift?.branchName}"
                ><span class="dot on"></span>Clock out<span class="hide-sm">&nbsp;of {app.shift?.branchName}</span></button
            >
        {:else if bid && here && app.shiftKnown}
            <button class="primary" onclick={doClockIn} disabled={clockBusy}>Clock in</button>
        {/if}

        <a class="btn" href="#/notifications" title="Alerts" onmouseenter={() => warm(load.notifications())}>
            Alerts{#if app.unread}<b class="count">{app.unread}</b>{/if}
        </a>
        <button class="hide-sm" onclick={() => (feedbackOpen = true)}>Feedback</button>
        <ThemeIcon />
        <span class="user muted ellipsis" title={app.me?.username}>{app.me?.displayName}</span>
        <button class="ghost" onclick={() => signOut()}>Sign out</button>
    </header>

    <nav class:open={menuOpen} aria-label="Main">
        {#each nav as group (group.title)}
            {#if group.items.some((i) => i.show)}
                <h3>{group.title}</h3>
                {#each group.items.filter((i) => i.show) as item (item.path)}
                    <a href="#{item.path}" class:active={section === item.path} onmouseenter={item.warm} onpointerdown={item.warm} onfocus={item.warm}
                        >{item.text}</a
                    >
                {/each}
            {/if}
        {/each}
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
            <ClientDetail id={route.current.parts[1]} />
        {:else if section === "/clients"}
            <Clients />
        {:else if section === "/sessions" && route.current.parts[1]}
            <SessionDetail id={route.current.parts[1]} />
        {:else if section === "/inventory"}
            <Inventory />
        {:else if section === "/finance"}
            <Finance />
        {:else if section === "/reports"}
            <Reports />
        {:else if section === "/remittance" && route.current.parts[1]}
            <RemittanceDetail id={route.current.parts[1]} />
        {:else if section === "/remittance"}
            <Remittances />
        {:else if section === "/relief"}
            <Relief />
        {:else if section === "/notifications"}
            <Notifications />
        {:else if section === "/users"}
            <Users />
        {:else if section === "/branches"}
            <Branches />
        {:else if section === "/catalog"}
            <Catalog />
        {:else if section === "/feedback"}
            <FeedbackList />
        {:else if section === "/audit"}
            <Audit />
        {:else}
            <div class="notice">Page not found. <a href="#/today">Go to Today</a></div>
        {/if}
    </main>
</div>

<style>
    .shell {
        display: grid;
        grid-template-columns: 168px 1fr;
        grid-template-rows: 40px 1fr;
        height: 100%;
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
    .dot {
        width: 8px;
        height: 8px;
        background: var(--text-3);
        display: inline-block;
    }
    .dot.on {
        background: var(--ok);
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
            width: 100%;
        }
        .branch {
            max-width: none;
            flex: 1;
            min-width: 120px;
        }
        .brand {
            display: none;
        }
        .day,
        .top > button:not(.menu):not(.primary),
        .top > .spacer {
            display: none;
        }
        .top > button.primary,
        .top > button.clock:not(.menu) {
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
