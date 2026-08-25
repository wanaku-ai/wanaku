import react from "@vitejs/plugin-react";
import {defineConfig} from "vite";

const outDir = "./dist";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    VITE_API_URL:
      process.env.VITE_API_URL ?? JSON.stringify(""),
    VITE_INFERENCE_URL:
      process.env.VITE_INFERENCE_URL ?? JSON.stringify(""),
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
