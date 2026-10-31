// Lets `node --test` load the UI sources. The sources use extensionless relative imports,
// as Vite does. This hook resolves them to `<path>.ts` or `<path>/index.ts`.
import { existsSync } from 'node:fs';
import { registerHooks } from 'node:module';
import { fileURLToPath } from 'node:url';

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.startsWith('.') && context.parentURL?.endsWith('.ts')) {
      const base = new URL(specifier, context.parentURL).href;
      const match = [`${base}.ts`, `${base}/index.ts`].find((url) =>
        existsSync(fileURLToPath(url)),
      );
      if (match) return nextResolve(match, context);
    }
    return nextResolve(specifier, context);
  },
});
