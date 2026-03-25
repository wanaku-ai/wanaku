import { viteRequire } from 'vite-require';
import { defineConfig } from 'vitest/config';
import * as path from "node:path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [viteRequire()],
  test: {
    globals: true,
    include: [
        '**/test/*.test.mjs',
        '**/test/index.test.mjs',
        '**/test/bindings/**/*.test.mjs',
        '**/test/definitions/**/*.test.mjs',
        '**/test/definitions/3.0.0/definitions.suite.mjs',
    ],
    reporters: ['verbose'] // https://vitest.dev/guide/reporters#custom-reporters
  },
  resolve: {
      alias: {
        // @ts-ignore
        '@extensions': path.resolve(__dirname, './extensions'),
        // @ts-ignore
        '@examples': path.resolve(__dirname, './examples'),
        // @ts-ignore
        '@definitions': path.resolve(__dirname, './definitions'),
        // @ts-ignore
        '@bindings': path.resolve(__dirname, './bindings'),
        // @ts-ignore
        '@common': path.resolve(__dirname, './common'),
        // @ts-ignore
        '@test': path.resolve(__dirname, './test'),
      },
    },
})
