import { defineConfig } from 'orval';

export default defineConfig({
    wanaku: {
        input: '../../wanaku-router-backend/src/main/webui/openapi.json',
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
            afterAllFilesWrite: 'prettier --write',
        },
    },
});