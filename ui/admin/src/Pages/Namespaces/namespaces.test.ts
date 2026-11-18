import assert from 'node:assert/strict';
import { test } from 'node:test';
import { concreteNamespaces, isVirtualNamespace, mcpAddress } from './namespaces';

const location = { protocol: 'https:', hostname: 'wanaku.apps.example.com' };

test('uses the public MCP URL when the server sets one', () => {
  assert.equal(
    mcpAddress('finance', 'https://wanaku-mcp.apps.example.com', location),
    'https://wanaku-mcp.apps.example.com/finance/mcp',
  );
});

test('removes trailing slashes from the public MCP URL', () => {
  assert.equal(
    mcpAddress('finance', 'https://wanaku-mcp.apps.example.com/', location),
    'https://wanaku-mcp.apps.example.com/finance/mcp',
  );
});

test('falls back to the admin UI host and port 8081 without a public MCP URL', () => {
  assert.equal(
    mcpAddress('finance', null, location),
    'https://wanaku.apps.example.com:8081/finance/mcp',
  );
  assert.equal(
    mcpAddress('finance', undefined, location),
    'https://wanaku.apps.example.com:8081/finance/mcp',
  );
});

test('uses the default namespace when the name is missing', () => {
  assert.equal(
    mcpAddress(undefined, null, location),
    'https://wanaku.apps.example.com:8081/default/mcp',
  );
});

test('uses the explicit virtual MCP endpoint', () => {
  assert.equal(
    mcpAddress('integration', 'https://mcp.example.com/', location, true),
    'https://mcp.example.com/virtual-ns/integration/mcp',
  );
  assert.equal(
    mcpAddress('integration', null, location, true),
    'https://wanaku.apps.example.com:8081/virtual-ns/integration/mcp',
  );
});

test('empty membership still identifies a virtual namespace', () => {
  assert.equal(isVirtualNamespace({ name: 'empty', members: [] }), true);
  assert.equal(isVirtualNamespace({ name: 'concrete' }), false);
  assert.equal(isVirtualNamespace({ name: 'concrete', members: null }), false);
});

test('registration choices contain concrete namespaces only, in display order', () => {
  const choices = concreteNamespaces([
    { name: 'zebra' },
    { name: 'integration', members: ['zebra'] },
    { name: 'empty', members: [] },
    { name: 'default' },
    { name: 'alpha', members: null },
  ]);
  assert.deepEqual(
    choices.map((namespace) => namespace.name),
    ['default', 'alpha', 'zebra'],
  );
});
