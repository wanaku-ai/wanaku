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

export function opaEvaluatorData() {
  return {
    ...evaluatorData(),
    name: `e2e-opa-evaluator-${suffix()}`,
    engine: {
      type: 'opa' as const,
      connection: 'local-policy',
      decision_path: 'wanaku/tool_call/allow',
    },
    processor: { path: 'actions/dist/opa_allow_action.wasm' },
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

// Policy simulation reports contain decision metadata, never request payloads.
export const simulationIdentity = {
  policy_revision: 1,
  evaluator_revision: 0,
  policy_checksum: "candidate-checksum",
};
export const simulationAction = {
  namespace: "default",
  request: {
    jsonrpc: "2.0",
    id: 1,
    method: "tools/call",
    params: { name: "summary", arguments: {} },
  },
};
export const simulationReport = {
  schema_version: "1.0",
  identity: simulationIdentity,
  stale: false,
  namespace: "default",
  operation: "tools/call",
  target: "summary",
  posture: { mode: "enforce", no_match: "allow", on_failure: "deny" },
  stages: [
    {
      stage: "action_policy",
      decision: "deny",
      matched_ids: ["protect-production"],
      reason_code: "production_restart_denied",
    },
    {
      stage: "evaluator",
      decision: "skipped",
      matched_ids: ["review"],
      reason_code: "external_evaluator_skipped",
    },
  ],
  decision: "deny",
  conditional: false,
  baseline_used: false,
  failure_used: false,
  redaction: {
    payload_captured: false,
    payload_truncated: false,
    redacted_fields: [],
  },
  duration_ms: 2,
};
export const simulationReplay = {
  schema_version: "1.0",
  id: "replay-1",
  status: "completed",
  identity: simulationIdentity,
  stale: false,
  duration_ms: 4,
  counts: { deny_to_allow: 1, allow_to_deny: 1 },
  skipped_audit_ids: ["audit-1"],
  details: [
    {
      index: 0,
      category: "deny_to_allow",
      access_expansion: true,
      compatibility_impact: false,
      outside_evidence: true,
      active: simulationReport,
      candidate: { ...simulationReport, decision: "allow" },
    },
    {
      index: 1,
      category: "allow_to_deny",
      access_expansion: false,
      compatibility_impact: true,
      outside_evidence: false,
      active: { ...simulationReport, decision: "allow" },
      candidate: simulationReport,
    },
  ],
};

export const simulationExampleCandidate = {
  base_policy_revision: 0,
  base_evaluator_revision: 0,
  inline_policy: {
    rules: [
      {
        id: "example-deny-tool-calls",
        description: "Block tool calls in this transient candidate.",
        effect: "deny",
        selectors: { operation: "tools/call", target_type: "tool" },
        reason_code: "example_tool_call_denied",
      },
    ],
  },
  inline_evaluators: [],
};

export const simulationAuditEvent = (id: string) => ({
  schema_version: "1.0",
  event_id: id,
  stream_id: "test",
  sequence: 1,
  timestamp: "2026-10-12T08:00:00Z",
  category: "decision",
  decision: "allow",
  correlation_id: id,
  protocol: "mcp",
  operation: "tools/call",
  namespace: "default",
  target: "summary",
  reason_code: "allowed",
  explanation: "Allowed.",
  redaction: {
    payload_captured: true,
    payload_truncated: false,
    redacted_fields: [],
  },
  payload: simulationAction.request,
  attributes: {},
  coverage_complete: true,
  dropped_events: 0,
});

export const actionPolicyRevision = (
  overrides?: Partial<{ id: number; activated_at: string | null; status: string; failure_reason: string | null }>,
) => ({
  id: 1,
  created_at: "2026-08-27T10:00:00Z",
  activated_at: "2026-08-27T10:00:00Z",
  status: "active",
  checksum: "test-checksum",
  origin: "startup",
  actor: null,
  failure_reason: null,
  ...overrides,
});
export const actionPolicy = {
  rules: [{
    id: "protect-production",
    description: "Protect production services",
    effect: "deny",
    selectors: { operation: "tools/call", target_type: "tool", target_name: { matcher: "glob", value: "restart*" } },
    predicates: [{ operator: "equals", pointer: "/arguments/service", value: "production" }],
    reason_code: "production_restart_denied",
    message: "Production restarts are not permitted.",
    metadata: { owner: "platform" },
  }],
};
