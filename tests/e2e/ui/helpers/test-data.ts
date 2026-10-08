let _counter = 0;
const suffix = () => {
  _counter++;
  return `${Date.now().toString(36)}-${_counter}`;
};

export const invalidHttpUrls = [
  'not-a-url',
  'localhost:9090',
  '/relative',
  'ftp://localhost:9090/',
  'file:///tmp/agent',
  'http:localhost:9090',
  'http://',
  'http://localhost:abc/',
  'http://localhost:99999/',
  'http://localhost:0/',
  'http://user:password@localhost:9090/',
  'http://localhost:9090/#fragment',
  ' http://localhost:9090/',
  'http://local host:9090/',
  'http://localhost:9090//rpc',
  'http://localhost:9090/../rpc',
  'http://localhost:9090/%2e%2e/rpc',
];

export const httpUrlNormalizationCases = [
  {
    label: 'uppercase schemes',
    address: 'HTTPS://agent.example/rpc',
    normalizedAddress: 'https://agent.example/rpc',
    cardAddress: 'HTTPS://cards.example/custom.json',
    normalizedCardAddress: 'https://cards.example/custom.json',
  },
  {
    label: 'Unicode hostnames and paths',
    address: 'https://münich.example/送信',
    normalizedAddress: 'https://xn--mnich-kva.example/%E9%80%81%E4%BF%A1',
    cardAddress: 'https://münich.example/卡.json',
    normalizedCardAddress: 'https://xn--mnich-kva.example/%E5%8D%A1.json',
  },
];

export function toolData(overrides?: Partial<{ name: string; description: string; uri: string; type: string; inputSchema: object }>) {
  return {
    name: overrides?.name ?? `e2e-tool-${suffix()}`,
    description: overrides?.description ?? 'E2E test tool',
    uri: overrides?.uri ?? 'https://example.com/api',
    type: overrides?.type ?? 'http',
    inputSchema: overrides?.inputSchema ?? { type: 'object', properties: {} },
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

export function forwardData(overrides?: Partial<{ name: string; address: string }>) {
  return {
    name: overrides?.name ?? `e2e-forward-${suffix()}`,
    address: overrides?.address ?? 'http://localhost:19999/mcp',
  };
}

export function namespaceData(overrides?: Partial<{ name: string }>) {
  return {
    name: overrides?.name ?? `e2e-ns-${suffix()}`,
  };
}

export function evaluatorData() {
  return {
    name: `e2e-evaluator-${suffix()}`,
    trigger: { method: 'tools/call', namespace: 'e2e-governance' },
    engine: { type: 'passthrough' as const },
    processor: { path: 'actions/dist/safety_review_action.wasm' },
  };
}

export function bindingData(overrides?: Partial<{
  id: string;
  forwardId: string;
  origin: string;
  mechanism: { type: string; header?: string };
  allowedPurposes: string[];
  secretRefs: string[];
  revision: number;
}>) {
  return {
    id: overrides?.id ?? `e2e-binding-${suffix()}`,
    forwardId: overrides?.forwardId ?? `e2e-forward-${suffix()}`,
    origin: overrides?.origin ?? 'https://api.example.com:443',
    mechanism: overrides?.mechanism ?? { type: 'bearer' },
    allowedPurposes: overrides?.allowedPurposes ?? ['invocation'],
    secretRefs: overrides?.secretRefs ?? ['vault:secret/data/example-token'],
    revision: overrides?.revision ?? 1,
  };
}

export function agentData(overrides?: Partial<{
  name: string; description: string; namespace: string; address: string; cardAddress: string;
}>) {
  return {
    name: overrides?.name ?? `e2e-agent-${suffix()}`,
    description: overrides?.description ?? 'E2E A2A agent',
    namespace: overrides?.namespace ?? 'default',
    address: overrides?.address ?? 'http://localhost:19999/',
    ...(overrides?.cardAddress ? { cardAddress: overrides.cardAddress } : {}),
  };
}
