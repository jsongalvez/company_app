<script lang="ts">
    import { app, canRead, signOut } from "../lib/app.svelte";
    import { today, selectBranch, clockIn, clockOut } from "../lib/today.svelte";
    import { route, go, goBack } from "../lib/router.svelte";
    import ThemeIcon from "./ThemeIcon.svelte";
    import FullIcon from "./FullIcon.svelte";
    import InstallApp from "./InstallApp.svelte";
    import Feedback from "./Feedback.svelte";
    import { load, warm } from "../lib/data";
    import { peek } from "../lib/cache.svelte";
    import NetBar from "./NetBar.svelte";
    import Lazy from "./Lazy.svelte";
    import { page } from "../lib/pages.svelte";
    import { navGroups } from "../lib/nav";
    import { day, label, operationalDate } from "../lib/format";
    import Today from "../pages/Today.svelte";
    import ShellSearch from "./ShellSearch.svelte";
    import ViewAsBar from "./ViewAsBar.svelte";

    const bid = $derived(app.branchId);
    const view = $derived(canRead("SEE_DAY"));
    const nav = $derived(navGroups(bid));

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
    // It starts after the menu has painted: run inside the tap, it held the menu back ~100ms on a slow phone.
    $effect(() => {
        if (!menuOpen) return;
        const items = nav.flatMap((group) => group.items.filter((item) => item.show));
        let timer: ReturnType<typeof setTimeout> | undefined;
        const frame = requestAnimationFrame(() => {
            timer = setTimeout(() => {
                for (const item of items) {
                    if (item.page) page(item.page);
                    item.reads?.()
                        .filter((l) => peek(l.key) === undefined)
                        .forEach(warm);
                }
            });
        });
        return () => {
            cancelAnimationFrame(frame);
            clearTimeout(timer);
        };
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
        goBack();
    }

    // Branch picker: home/relief branches first, then all-branch read access.
    const branchOptions = $derived.by(() => {
        const mine = app.branches.map((b) => ({ id: b.branchId, name: b.branchName, note: b.isRelief ? " (relief)" : "" }));
        const seen = new Set(mine.map((b) => b.id));
        const rest = app.accessible.filter((b) => !seen.has(b.id)).map((b) => ({ id: b.id, name: b.name, note: "" }));
        return { mine, rest };
    });

    const here = $derived(app.branches.find((b) => b.branchId === bid));
    const clockedHere = $derived(today.shift?.branchId === bid);
    const clockedElsewhere = $derived(!!today.shift && today.shift.branchId !== bid);
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

    // Default landing page.
    $effect(() => {
        if (route.current.parts.length === 0) go(view ? "/today" : "/relief");
    });

    const opDate = $derived(operationalDate());
</script>

<NetBar />
<div class="shell" class:viewing={!!app.viewAs}>
    <ViewAsBar />
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

        <ShellSearch onpick={() => (menuOpen = false)} />

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
            {#if today.day?.status && today.day.status !== "OPEN"}
                <span class="mark {today.day.status === 'REMITTED' ? 'info' : 'plain'}">{label(today.day.status)}</span>
            {/if}
        </span>

        <span class="spacer"></span>

        {#if clockedHere}
            <button class="clock ok" onclick={doClockOut} disabled={clockBusy} title="On duty here{today.shift?.isRelief ? ' (relief)' : ''}">Clock out</button>
        {:else if clockedElsewhere}
            <button class="clock" onclick={doClockOut} disabled={clockBusy} title="Clock out of {today.shift?.branchName}"
                >Clock out<span class="hide-sm">&nbsp;of {today.shift?.branchName}</span></button
            >
        {:else if bid && here && today.shiftKnown}
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
        <div class="navfoot faint">
            <InstallApp />
            <button class="ghost sm menu-only" onclick={() => (feedbackOpen = true)}>Send feedback</button>
            <button class="ghost sm menu-only" onclick={() => signOut()}>Sign out</button>
        </div>
    </nav>

    <Feedback bind:open={feedbackOpen} />

    <main>
        {#if !bid && section !== "/relief"}
            <div class="notice">You aren't assigned to a branch yet. Ask a manager to assign you, or request <a href="#/relief">relief duty</a>.</div>
        {/if}
        {#if !bid && ["/today", "/inventory", "/finance", "/reports", "/remittance"].includes(section)}
            <!-- these pages show one branch, and there is none -->
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
    .top {
        grid-column: 1 / -1;
        display: flex;
        align-items: center;
        gap: var(--s2);
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
        padding: 0 var(--s1);
        font-size: 11px;
        line-height: 16px;
    }
    .menu {
        display: none;
        font-size: var(--t-row);
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
        display: flex;
        flex-wrap: wrap;
        gap: var(--s2);
        padding: 12px 10px;
        margin-top: auto;
    }
    .menu-only {
        display: none;
    }
    main {
        overflow: auto;
        padding: var(--s3);
        min-width: 0;
    }

    /* Tablet: drop secondary top-bar items. */
    @media (max-width: 1100px) {
        .user,
        .top > button.ghost:last-child {
            display: none;
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
        .top {
            flex-wrap: wrap;
            padding: 6px 8px;
            gap: 6px;
        }
        .menu {
            display: inline-flex;
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
        .menu-only {
            display: inline-flex;
        }
        main {
            padding: var(--s2);
        }
    }
</style>
