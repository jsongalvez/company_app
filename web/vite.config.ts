import { defineConfig, type Plugin } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

// Dev: proxy the API to the local backend (APP_PORT from ../.env, default 8080).
const backend = `http://localhost:${process.env.APP_PORT ?? "8080"}`;

// The signed-in app is its own file (src/lib/shell.ts). A signed-in open starts fetching it with the page
// script instead of after it; a signed-out open leaves the line free for the sign-in screen.
function preloadShellWhenSignedIn(): Plugin {
    return {
        name: "preload-shell-when-signed-in",
        apply: "build",
        transformIndexHtml: {
            order: "post",
            handler(_html, ctx) {
                const shell = Object.values(ctx.bundle ?? {}).find((c) => c.type === "chunk" && c.facadeModuleId?.endsWith("/components/Shell.svelte"));
                if (!shell || shell.type !== "chunk") throw new Error("no Shell chunk: is it still loaded lazily?");
                const files = [shell.fileName, ...(shell.viteMetadata?.importedCss ?? [])].map((f) => `/${f}`);
                const code = `if(localStorage.getItem("token"))for(const f of ${JSON.stringify(files)}){const l=document.createElement("link");if(f.endsWith(".css")){l.rel="preload";l.as="style"}else l.rel="modulepreload";l.href=f;document.head.appendChild(l)}`;
                return [{ tag: "script", children: code, injectTo: "head" }];
            },
        },
    };
}

export default defineConfig({
    plugins: [svelte(), preloadShellWhenSignedIn()],
    define: { __APP_VERSION__: JSON.stringify(new Date().toISOString().slice(0, 16).replace("T", " ")) },
    server: {
        port: 5173,
        proxy: {
            "/api": backend,
            "/auth": backend,
            "/health": backend,
        },
    },
    build: { target: "es2022", sourcemap: true },
});
