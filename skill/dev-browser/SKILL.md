---
name: dev-browser
description: "Browser automation with persistent page state using dev-browser CLI (Playwright + QuickJS sandbox). Use when verification criteria say 'Verify in browser using dev-browser skill', when a UI/API needs visual or functional verification, or when asked to navigate, fill forms, take screenshots, scrape, automate, or test in a browser. Triggers on: go to [url], click on, fill out the form, take a screenshot, scrape, automate, test the website, verify in browser, verify UI, check the UI."
---

# Dev Browser — CompanyApp Verification

Browser automation CLI for verifying the CompanyApp backend API and frontend.
Uses Playwright under the hood with a sandboxed QuickJS runtime.

## Installation

```bash
npm install -g --prefix ~/.local dev-browser
dev-browser install   # downloads Chromium
```

The binary is at `~/.local/bin/dev-browser`.

## Project Backend

| Setting   | Value                      |
|-----------|----------------------------|
| Base URL  | `http://localhost:3023`    |
| API path  | `/api/*`                   |
| Auth      | JWT Bearer token           |
| Login     | `POST /api/auth/login`     |
| Swagger   | `http://localhost:3023/swagger` (if configured) |

## Primary Use: Backend API Verification

dev-browser can verify API endpoints by navigating to their URLs and inspecting
responses. Use this to test that endpoints return correct status codes, JSON
structures, and to visually verify API documentation pages.

```
dev-browser <<'EOF'
const page = await browser.getPage("api");
await page.goto("http://localhost:3023/api/branches", { waitUntil: "domcontentloaded" });
console.log(JSON.stringify({
  url: page.url(),
  body: await page.textContent("body"),
}, null, 2));
EOF
```

## Secondary Use: Compose Desktop App via --connect

The desktop Compose app can expose a WebView or connect to an existing browser
for testing. If a browser instance is running with remote debugging:

```bash
# Launch Chrome with debugging
google-chrome --remote-debugging-port=9222 &

# Connect and inspect
dev-browser --connect http://localhost:9222 <<'EOF'
const tabs = await browser.listPages();
console.log(JSON.stringify(tabs, null, 2));
EOF
```

---

## Full dev-browser API Reference

Scripts run in a sandboxed QuickJS WASM runtime (NOT Node.js). Available globals:
- `browser` — pre-connected Playwright browser handle
- `console` — log, warn, error, info
- `setTimeout` / `clearTimeout`
- `saveScreenshot(buf, name)` — save screenshot to `~/.dev-browser/tmp/<name>`
- `writeFile(name, data)` — write a file to `~/.dev-browser/tmp/<name>`
- `readFile(name)` — read a file from `~/.dev-browser/tmp/<name>`

NOT available: `require()`, `import()`, `process`, `fs`, `fetch`, `__dirname`.

### browser API

```
browser.getPage(nameOrId)    Get/create named page (persists between scripts)
browser.newPage()            Create anonymous page (cleaned up after script)
browser.listPages()          List all tabs [{id, url, title, name}]
browser.closePage(name)      Close and remove a named page
```

### Page Methods (Playwright API)

```
page.goto(url, { waitUntil: "domcontentloaded" })   Navigate (use domcontentloaded for dev servers)
page.title()                 Get page title
page.url()                   Get current URL
page.content()               Get full HTML
page.textContent(selector)   Get text content of element
page.innerHTML(selector)     Get inner HTML of element
page.fill(selector, value)   Fill input
page.click(selector)         Click element
page.type(selector, text)    Type text char by char
page.press(selector, key)    Press a key (Enter, Tab, etc.)
page.waitForSelector(s)      Wait for element to appear
page.waitForURL(url)         Wait for navigation
page.screenshot()            Capture screenshot buffer
page.evaluate(fn)            Run JS in page context
page.locator(selector)       Create locator for chained actions
page.$(selector)             Query single element
page.$$(selector)            Query all elements
page.$eval(selector, fn)     Eval on first matching element
page.$$eval(selector, fn)    Eval on all matching elements
```

### AI-Optimized Methods

```
page.snapshotForAI(options)   Get AI snapshot { full, incremental? }
                               Options: { track?, depth?, timeout? }
page.cua.screenshot(options)  Vision workflow screenshot { path, width, height }
                               Options: { name?, fullPage?, clip? }
page.cua.click({ x, y })      Click at viewport coordinates
page.cua.doubleClick({x, y})
page.cua.type({ text })
page.cua.keypress({ keys: ["ctrl", "a"] })
page.cua.scroll({ x, y, scrollX, scrollY })
page.cua.drag({ path: [{x, y}, ...] })
page.domCua.getVisibleDom()   Snapshot visible interactive elements as node_id=N
page.domCua.click({ nodeId }) Click by node id from latest snapshot
```

### CLI Options

```
--browser <NAME>         Named browser instance (default: "default")
--connect [<URL>]        Connect to running Chrome (auto-discover or specific CDP URL)
--headless               Launch Chromium headless
--ignore-https-errors    Ignore SSL certificate errors
--timeout <SECONDS>      Max script time (default: 30)
```

### Common Patterns

**Inspect a page:**
```
dev-browser <<'EOF'
const page = await browser.getPage("main");
await page.goto("http://localhost:3023/api/health", { waitUntil: "domcontentloaded" });
const snapshot = await page.snapshotForAI();
console.log(snapshot.full);
EOF
```

**Fill a form (login):**
```
dev-browser <<'EOF'
const page = await browser.getPage("login");
await page.goto("http://localhost:3023/login", { waitUntil: "domcontentloaded" });
await page.fill('input[name="username"]', "superuser");
await page.fill('input[name="password"]', "password");
await page.click('button[type="submit"]');
await page.waitForURL("**/dashboard");
console.log("Logged in:", page.url());
EOF
```

**Test an API endpoint response:**
```
dev-browser <<'EOF'
const page = await browser.getPage("api");
const response = await page.goto("http://localhost:3023/api/branches", { waitUntil: "domcontentloaded" });
console.log("Status:", response.status());
console.log("Body:", await page.textContent("body"));
EOF
```

**Take a screenshot for visual checks:**
```
dev-browser <<'EOF'
const page = await browser.getPage("main");
await page.goto("http://localhost:3023/some-page", { waitUntil: "domcontentloaded" });
const buf = await page.screenshot();
const path = await saveScreenshot(buf, "verification.png");
console.log("Screenshot saved:", path);
EOF
```

**Vision workflow (click by coordinates):**
```
dev-browser <<'EOF'
const page = await browser.getPage("app");
const shot = await page.cua.screenshot();
console.log(JSON.stringify(shot));
// Read path in output, measure coordinates, then:
// const page = await browser.getPage("app");
// await page.cua.click({ x: 412, y: 233 });
EOF
```

**DOM-id workflow:**
```
dev-browser <<'EOF'
const page = await browser.getPage("app");
console.log(await page.domCua.getVisibleDom());
// Read node_id= values, then:
// await page.domCua.click({ nodeId: 2 });
EOF
```

## Project-Specific Verification Scripts

### Health Check
```bash
dev-browser <<'EOF'
const page = await browser.getPage("health");
const response = await page.goto("http://localhost:3023/api/health", { waitUntil: "domcontentloaded" });
console.log("Backend status:", response.status());
console.log(await page.textContent("body"));
EOF
```

### Login and Get Token
```bash
dev-browser <<'EOF'
const page = await browser.getPage("auth");
const response = await page.goto("http://localhost:3023/api/auth/login", {
  waitUntil: "domcontentloaded"
});
// Note: Login is typically POST with JSON body; for GET-based health checks use the pattern above
// For actual login testing, use curl or the Ktor client instead
console.log("Auth response:", response.status());
EOF
```

## Running the Compose Desktop App

For UI verification beyond what dev-browser can do with the API:

```bash
# Start backend
docker compose -f docker/docker-compose.yml up -d

# Launch desktop app (opens native window)
./gradlew composeApp:desktopRun
```

The desktop Compose window should match the target screen design. Verify:
- All composables render without crash
- Buttons, inputs, and navigation work
- States load correctly (loading, data, empty, error)
- Theming matches Material3 design tokens
