import { readFileSync } from "node:fs";
import react from "@vitejs/plugin-react";
import {defineConfig} from "vite";

const outDir = "./dist";
const workspacePackage = readFileSync(new URL("../../Cargo.toml", import.meta.url), "utf8")
  .split(/^\[/m).find((section) => section.startsWith("workspace.package]"));
const serverVersion = workspacePackage?.match(/^version\s*=\s*"([^"]+)"/m)?.[1];
if (!serverVersion) throw new Error("workspace.package.version is missing from Cargo.toml");

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    WANAKU_VERSION: JSON.stringify(serverVersion),
    VITE_API_URL:
      process.env.VITE_API_URL ?? JSON.stringify(""),
  },
  build: {
    outDir,
    sourcemap: false,
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            if (id.includes("@carbon/")) {
              return "vendor-carbon";
            }
            if (id.includes("react-markdown") || id.includes("highlight.js")) {
              return "vendor-markdown";
            }
            if (id.includes("react-router")) {
              return "vendor-router";
            }
          }
        },
      },
    },
  },
  base: "/admin/",
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern-compiler",
      },
    },
  },
});
