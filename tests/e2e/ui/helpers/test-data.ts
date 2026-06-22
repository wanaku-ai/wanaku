const suffix = () => Date.now().toString(36);

export function toolData(overrides?: Partial<{ name: string; description: string; uri: string; type: string }>) {
  return {
    name: overrides?.name ?? `e2e-tool-${suffix()}`,
    description: overrides?.description ?? 'E2E test tool',
    uri: overrides?.uri ?? 'https://example.com/api',
    type: overrides?.type ?? 'http',
  };
}

export function resourceData(overrides?: Partial<{ name: string; description: string; location: string; type: string; mimeType: string }>) {
  return {
    name: overrides?.name ?? `e2e-resource-${suffix()}`,
    description: overrides?.description ?? 'E2E test resource',
    location: overrides?.location ?? '/tmp/e2e-test.json',
    type: overrides?.type ?? 'file',
    mimeType: overrides?.mimeType ?? 'application/json',
  };
}

export function promptData(overrides?: Partial<{ name: string; description: string }>) {
  return {
    name: overrides?.name ?? `e2e-prompt-${suffix()}`,
    description: overrides?.description ?? 'E2E test prompt',
  };
}
