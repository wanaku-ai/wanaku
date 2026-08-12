import react from "@vitejs/plugin-react";
import {defineConfig} from "vite";

const outDir = "./dist";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
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
            if (id.includes("@carbon/react") || id.includes("@carbon/icons-react")) {
              return "vendor-carbon";
            }
            if (id.includes("react-markdown") || id.includes("highlight.js")) {
              return "vendor-markdown";
            }
            if (id.includes("react-router-dom")) {
              return "vendor-router";
            }
            return "vendor";
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
