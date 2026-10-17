import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

// Dev: proxy the API to the local backend (APP_PORT from ../.env, default 8080).
const backend = `http://localhost:${process.env.APP_PORT ?? "8080"}`;

export default defineConfig({
  plugins: [svelte()],
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
