import { defineConfig } from 'orval';

export default defineConfig({
    wanaku: {
        input: '../routers/wanaku-router/src/main/webui/openapi.json',
        output: {
            mode: 'single',
            target: './src/api/wanaku-router-api.ts',
            schemas: './src/models',
            mock: false,
          },
        hooks: {
            afterAllFilesWrite: 'prettier --write',
        },
    },
});