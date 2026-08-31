import {defineConfig} from 'orval';

export default defineConfig({
    wanaku: {
        input: {
            target: './openapi.json',
            override: {
                // The server publishes the management response envelope. The
                // custom fetch adapter removes that envelope before it returns.
                transformer: (spec) => {
                    for (const pathItem of Object.values(spec.paths ?? {})) {
                        for (const operation of Object.values(pathItem ?? {})) {
                            if (!operation || typeof operation !== 'object' || !('tags' in operation)) continue;
                            if (!operation.tags?.some(tag => tag === 'Evaluators' || tag === 'Action Policies')) continue;

                            if (operation.operationId?.startsWith('activate_') && operation.requestBody) {
                                operation.requestBody.required = false;
                            }

                            const schema = operation.responses?.['200']?.content?.['application/json']?.schema;
                            if (!schema || !('$ref' in schema)) continue;

                            const name = schema.$ref.split('/').at(-1);
                            const envelope = name ? spec.components?.schemas?.[name] : undefined;
                            if (envelope && !('$ref' in envelope) && envelope.properties?.data) {
                                operation.responses!['200']!.content!['application/json']!.schema = envelope.properties.data;
                            }
                        }
                    }
                    return spec;
                },
            },
        },
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
