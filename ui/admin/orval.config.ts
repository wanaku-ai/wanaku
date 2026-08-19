import {defineConfig} from 'orval';

export default defineConfig({
    wanaku: {
        input: './openapi.json',
        output: {
            mode: 'single',
            target: './src/api/wanaku-router-api.ts',
            schemas: './src/models',
            client: 'fetch',
            mock: false,
            override: {
                mutator: {
                  path: './src/custom-fetch.ts',
                  name: 'customFetch',
                },
              },
          },
        hooks: {
            afterAllFilesWrite: 'npx prettier --write',
        },
    },
});