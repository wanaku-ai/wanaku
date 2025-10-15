import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const outDir = "./dist";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    VITE_API_URL:
      process.env.VITE_API_URL ??
      JSON.stringify("http://localhost:8080/api/v1"),
  },
  build: {
    outDir,
    sourcemap: true,
    emptyOutDir: true,
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
