import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { createServer as createSocket } from 'node:net';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { once } from 'node:events';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Run against three built repositories. All services, provider fixtures and state are local.
const [barnArgument, wsrArgument] = process.argv.slice(2);
if (!barnArgument || !wsrArgument) throw new Error('Usage: node tests/semantic-router/acceptance.mjs BARN_CHECKOUT WSR_CHECKOUT');
const barn = resolve(barnArgument);
const wsr = resolve(wsrArgument);
const wanaku = fileURLToPath(new URL('../../', import.meta.url));
const work = await mkdtemp(resolve(tmpdir(), 'wanaku-semantic-acceptance-'));
const processes = [];
const delay = ms => new Promise(done => setTimeout(done, ms));
let providerCalls = 0;

async function freePort() {
  const socket = createSocket();
  await new Promise(done => socket.listen(0, '127.0.0.1', done));
  const port = socket.address().port;
  await new Promise(done => socket.close(done));
  return port;
}

function launch(name, command, args, env = {}) {
  const log = createWriteStream(resolve(work, `${name}.log`), { flags: 'a' });
  const child = spawn(command, args, { cwd: work, env: { ...process.env, ...env }, detached: process.platform !== 'win32', stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(log);
  child.stderr.pipe(log);
  child.on('error', error => log.write(String(error)));
  processes.push(child);
  return child;
}

async function stop(child, signal = 'SIGTERM') {
  if (child.exitCode !== null || child.signalCode !== null) return;
  const exited = new Promise(done => child.once('exit', done));
  if (process.platform === 'win32') child.kill(signal);
  else process.kill(-child.pid, signal);
  await Promise.race([exited, delay(8000)]);
  if (child.exitCode === null && child.signalCode === null) process.kill(-child.pid, 'SIGKILL');
}

async function json(url, method = 'GET', data) {
  const response = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(15000) });
  const body = await response.text();
  assert(response.ok, `${method} ${url}: ${response.status} ${body}`);
  return body ? JSON.parse(body) : null;
}

async function ready(url, child, predicate = () => true) {
  const until = Date.now() + 90000;
  while (Date.now() < until) {
    if (child.exitCode !== null || child.signalCode !== null) throw new Error(`Service exited before readiness at ${url}; logs: ${work}`);
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1500) });
      if (response.ok && predicate(await response.json())) return;
    } catch { /* Retry while the process is starting. */ }
    await delay(250);
  }
  throw new Error(`Readiness timed out at ${url}; logs: ${work}`);
}

class Mcp {
  constructor(url) { this.url = url; this.id = 0; this.session = undefined; }
  async request(method, params) {
    const id = ++this.id;
    const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream', 'MCP-Protocol-Version': '2025-11-25' };
    if (this.session) headers['Mcp-Session-Id'] = this.session;
    const response = await fetch(this.url, { method: 'POST', headers, body: JSON.stringify({ jsonrpc: '2.0', id, method, params }), signal: AbortSignal.timeout(15000) });
    assert(response.ok, `MCP ${method}: HTTP ${response.status}`);
    this.session = response.headers.get('mcp-session-id') ?? this.session;
    const text = await response.text();
    const bodies = response.headers.get('content-type')?.includes('text/event-stream')
      ? text.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => JSON.parse(line.slice(5).trim()))
      : [JSON.parse(text)];
    const body = bodies.find(value => value.id === id);
    assert(body, `MCP ${method}: missing response ${id}`);
    return body;
  }
  async initialize() {
    const response = await this.request('initialize', { protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'semantic-acceptance', version: '1' } });
    assert(response.result?.capabilities.tools);
    const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream', 'MCP-Protocol-Version': '2025-11-25' };
    if (this.session) headers['Mcp-Session-Id'] = this.session;
    const notified = await fetch(this.url, { method: 'POST', headers, body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }), signal: AbortSignal.timeout(5000) });
    assert(notified.ok);
  }
  call(message) { return this.request('tools/call', { name: 'route_support', arguments: { message } }); }
}

const provider = createServer(async (request, response) => {
  providerCalls++;
  const parts = [];
  for await (const part of request) parts.push(part);
  const input = JSON.parse(Buffer.concat(parts).toString());
  const message = String(input.state);
  response.setHeader('Content-Type', 'application/json');
  if (message === 'provider_failure') { response.writeHead(503).end('{}'); return; }
  const choice = message === 'malformed' ? 'invalid_label' : message.includes('billing') ? 'billing' : message.includes('technical') ? 'technical' : message.startsWith('sink') ? 'sink' : 'no_match';
  const answers = Object.fromEntries(Object.entries(input.questions).map(([key, question]) => [key, {
    type: 'choice', choice, confidence: 1,
    probabilities: Object.fromEntries(Object.keys(question.criteria).map(label => [label, label === choice ? 1 : 0])),
  }]));
  response.end(JSON.stringify({ model: 'fixture', usage: { input_tokens: 1, output_tokens: 1 }, answers }));
});

try {
  await new Promise(done => provider.listen(0, '127.0.0.1', done));
  const ports = Object.fromEntries(await Promise.all(['barn', 'management', 'mcp', 'inference', 'a2a', 'wsr', 'status', 'preview'].map(async key => [key, await freePort()])));
  const barnUrl = `http://127.0.0.1:${ports.barn}`;
  const management = `http://127.0.0.1:${ports.management}`;
  const statusUrl = `http://127.0.0.1:${ports.status}/api/v1/status`;
  const providerUrl = `http://127.0.0.1:${provider.address().port}`;
  const expertArguments = [
    '--expert', 'supportExpert=org.apache.camel.component.typesafeai.TypeSafeAiSemanticAdapter',
    '--property', `camel.component.typesafe-ai.base-url=${providerUrl}`,
    '--property', 'camel.component.typesafe-ai.api-key={{env:WSR_ACCEPTANCE_API_KEY}}',
    '--property', 'camel.component.typesafe-ai.model=fixture',
    '--property', 'camel.component.typesafe-ai.request-timeout=5000',
    '--property', 'camel.component.typesafe-ai.max-concurrent-requests=4',
  ];
  const providerEnvironment = {
    WSR_ACCEPTANCE_API_KEY: 'fixture', TYPESAFE_API_KEY: 'fixture', TYPESAFE_MODEL: 'fixture',
  };
  const runtimeJar = resolve(wsr, 'target/wanaku-semantic-router-0.1.0-SNAPSHOT.jar');
  const preview = launch('preview', 'java', [
    '-jar', runtimeJar, 'preview', '--port', String(ports.preview), ...expertArguments,
  ], providerEnvironment);
  // POST readiness also proves the deployed adapter and local provider agree.
  await delay(1000);
  const barnProcess = launch('barn', 'java', [`-Dquarkus.http.host=127.0.0.1`, `-Dquarkus.http.port=${ports.barn}`, `-Dwanaku.home=${resolve(work, 'barn-home')}`, '-Dwanaku.persistence.infinispan.file-store=false', '-Dquarkus.infinispan-embedded.clustered=false', `-Dwanaku.semantic.preview-url=http://127.0.0.1:${ports.preview}/api/v1/preview`, '-jar', resolve(barn, 'apps/wanaku-barn-backend/target/quarkus-app/quarkus-run.jar')]);
  await ready(`${barnUrl}/api/v1/semantic-routers/actions`, barnProcess);
  console.log(`Services: Barn ${barnUrl}; preview http://127.0.0.1:${ports.preview}/api/v1/preview; logs ${work}`);
  assert(preview.exitCode === null, `Preview startup failed; logs: ${work}`);
  const remoteName = 'acceptance-billing-action';
  const remoteYaml = (await readFile(resolve(barn, 'apps/wanaku-barn-backend/src/main/resources/semantic-actions/wsr-billing-action.kamelet.yaml'), 'utf8'))
    .replace('name: wsr-billing-action', `name: ${remoteName}`);
  const remote = (await json(`${barnUrl}/api/v1/kamelets`, 'POST', { yaml: remoteYaml })).data;
  assert.equal(remote.name, remoteName);
  assert.equal(remote.semanticEligible, true);
  assert.match(remote.sha256, /^[a-f0-9]{64}$/);
  assert((await json(`${barnUrl}/api/v1/semantic-routers/actions`)).data.some(action => action.id === remoteName && action.sha256 === remote.sha256));
  const raw = await fetch(`${barnUrl}/api/v1/kamelets/${remoteName}.kamelet.yaml`);
  assert.equal(raw.status, 200);
  assert.match(raw.headers.get('content-type'), /yaml/);
  assert.equal(await raw.text(), remoteYaml);
  const ordinaryName = 'acceptance-ordinary-action';
  const ordinaryYaml = `apiVersion: camel.apache.org/v1\nkind: Kamelet\nmetadata:\n  name: ${ordinaryName}\n  labels:\n    camel.apache.org/kamelet.type: action\nspec:\n  definition:\n    title: Remote catalog probe\n    description: Verify ordinary Kamelet HTTP loading.\n    type: object\n    properties: {}\n  dependencies:\n    - camel:core\n  template:\n    from:\n      uri: kamelet:source\n      steps:\n        - setProperty:\n            name: probe-response\n            constant: "Remote catalog ✓"\n        - setBody:\n            exchange-property: probe-response\n        - to: kamelet:sink\n`;
  const ordinary = (await json(`${barnUrl}/api/v1/kamelets`, 'POST', { yaml: ordinaryYaml })).data;
  assert.equal(ordinary.semanticEligible, false);
  assert((await json(`${barnUrl}/api/v1/kamelets`)).data.some(entry => entry.name === ordinaryName));
  assert(!(await json(`${barnUrl}/api/v1/semantic-routers/actions`)).data.some(action => action.id === ordinaryName));
  const nativeProbe = launch('remote-camel', 'java', ['-cp', runtimeJar, resolve(wanaku, 'tests/semantic-router/RemoteKameletProbe.java'), `${barnUrl}/api/v1/kamelets/`, ordinaryName, 'Remote catalog ✓']);
  const [nativeExit] = await Promise.race([
    once(nativeProbe, 'exit'),
    delay(30000).then(() => { throw new Error(`Native Camel lookup timed out; logs: ${work}`); }),
  ]);
  assert.equal(nativeExit, 0, `Native Camel lookup failed; logs: ${work}`);
  const sinkName = 'acceptance-log-sink';
  const sinkYaml = `apiVersion: camel.apache.org/v1\nkind: Kamelet\nmetadata:\n  name: ${sinkName}\n  labels:\n    camel.apache.org/kamelet.type: sink\nspec:\n  definition:\n    title: Native sink destination\n    description: Send sink requests to the configured channel.\n    type: object\n    required: [channel]\n    properties:\n      channel:\n        title: Channel\n        type: string\n  dependencies:\n    - camel:core\n    - camel:kamelet\n  template:\n    from:\n      uri: kamelet:source\n      steps:\n        - choice:\n            when:\n              - simple: "\${body} == 'sink_failure'"\n                steps:\n                  - throwException:\n                      exceptionType: java.lang.IllegalStateException\n                      message: Sink delivery failed\n        - log:\n            message: "Sink accepted {{channel}}: \${body}"\n`;
  const sink = (await json(`${barnUrl}/api/v1/kamelets`, 'POST', { yaml: sinkYaml })).data;
  assert.equal(sink.semanticEligible, true, sink.semanticEligibilityReason);
  const sinkAction = (await json(`${barnUrl}/api/v1/semantic-routers/actions`)).data.find(action => action.id === sinkName);
  assert.equal(sinkAction.type, 'sink');
  assert.equal(sinkAction.sha256, sink.sha256);
  assert.equal(sinkAction.inputSchema.type, 'string');
  assert.equal(sinkAction.outputSchema.type, 'string');
  let pipeline = await readFile(resolve(wanaku, 'server/src/default.yaml'), 'utf8');
  for (const [original, key] of [[8081, 'mcp'], [8083, 'inference'], [8084, 'a2a']]) pipeline = pipeline.replaceAll(`:${original}`, `:${ports[key]}`);
  await writeFile(resolve(work, 'pipeline.yaml'), pipeline);
  // The normal evaluator stays in the pipeline. This fixture permits its unmatched
  // decision in this namespace and tests explicit action-policy allow/deny rules.
  await writeFile(resolve(work, 'wanaku.yaml'), 'governance:\n  namespaces:\n    semantic-acceptance:\n      no_match: allow\naction_policy:\n  rules:\n    - id: initial-deny\n      effect: deny\n      selectors:\n        namespace: semantic-acceptance\n        operation: tools/call\n');
  const router = launch('wanaku', resolve(wanaku, 'target/debug/wanaku-server'), ['--pipeline-config', resolve(work, 'pipeline.yaml'), '--wanaku-config', resolve(work, 'wanaku.yaml')], { WANAKU_MGMT_LISTEN: `127.0.0.1:${ports.management}`, WANAKU_PERSIST_BACKEND: 'none', WANAKU_FORWARD_HEALTHCHECK_INTERVAL: '1' });
  await ready(`${management}/api/v1/management/info`, router);
  await json(`${management}/api/v1/namespaces`, 'POST', { name: 'semantic-acceptance' });
  const definition = {
    name: 'Support-acceptance', description: 'Route a support message', toolName: 'route_support', profile: 'message-to-string/v1', expertId: 'support', semanticInput: 'message',
    instructions: 'Choose one configured support department.', noMatchCriteria: 'The message is outside billing and technical support.',
    actions: [
      { actionId: remoteName, sha256: remote.sha256, label: 'billing', criteria: 'Billing requests', configuration: { prefix: 'Acceptance' } },
      { actionId: 'wsr-technical-action', label: 'technical', criteria: 'Technical requests', configuration: { prefix: 'Acceptance' } },
      { actionId: sinkName, sha256: sink.sha256, label: 'sink', criteria: 'Sink requests', configuration: { channel: 'inbox' } },
    ], examples: [{ message: 'billing', expectedLabel: 'billing' }, { message: 'technical', expectedLabel: 'technical' }],
  };
  assert.equal((await json(`${barnUrl}/api/v1/semantic-routers/validate`, 'POST', definition)).data.valid, true);
  const storedBeforeFiles = (await json(`${barnUrl}/api/v1/data-store`)).data.length;
  const files = (await json(`${barnUrl}/api/v1/semantic-routers/files`, 'POST', definition)).data;
  assert(files['service/router.camel.yaml'].includes(definition.toolName));
  assert(files['service/preview.camel.yaml'].includes('semantic'));
  assert.equal(files[`service/kamelets/${remoteName}.kamelet.yaml`], remoteYaml);
  assert.equal(files[`service/kamelets/${sinkName}.kamelet.yaml`], sinkYaml);
  assert(files['service/semantic-router.properties'].includes('catalog.revision=preview'));
  assert.equal((await json(`${barnUrl}/api/v1/semantic-routers`)).data.length, 0);
  assert.equal((await json(`${barnUrl}/api/v1/data-store`)).data.length, storedBeforeFiles, 'Viewing generated files must not persist Kamelet revisions');
  assert.equal(providerCalls, 0, 'Viewing generated files must not call the provider');
  const saved = (await json(`${barnUrl}/api/v1/semantic-routers`, 'POST', definition)).data;
  assert.equal(saved.actions[0].sha256, remote.sha256);
  assert.match(saved.actions[1].sha256, /^[a-f0-9]{64}$/);
  const remoteReplacement = (await json(`${barnUrl}/api/v1/kamelets`, 'POST', { yaml: remoteYaml.replace('Billing demo response', 'Changed billing response') })).data;
  assert.notEqual(remoteReplacement.sha256, remote.sha256);
  const historical = (await json(`${barnUrl}/api/v1/semantic-routers/actions?definitionId=${saved.id}`)).data;
  assert(historical.some(action => action.id === remoteName && action.sha256 === remote.sha256));
  const pinnedFiles = (await json(`${barnUrl}/api/v1/semantic-routers/files`, 'POST', saved)).data;
  assert.equal(pinnedFiles[`service/kamelets/${remoteName}.kamelet.yaml`], remoteYaml);
  await json(`${barnUrl}/api/v1/kamelets/${remoteName}`, 'DELETE');
  assert.equal((await fetch(`${barnUrl}/api/v1/kamelets/${remoteName}.kamelet.yaml`)).status, 404);
  const retained = await fetch(`${barnUrl}/api/v1/kamelets/${remoteName}.kamelet.yaml?sha256=${remote.sha256}`);
  assert.equal(retained.status, 200);
  assert.equal(await retained.text(), remoteYaml);
  for (const label of ['billing', 'technical', 'sink', 'no_match']) {
    const result = (await json(`${barnUrl}/api/v1/semantic-routers/${saved.id}/preview`, 'POST', { message: label })).data;
    assert.equal(result.label, label);
    assert(result.durationMillis >= 0);
  }
  const published = (await json(`${barnUrl}/api/v1/semantic-routers/${saved.id}/publish`, 'POST')).data;
  assert.equal(published.status, 'published');
  assert.equal((await json(`${barnUrl}/api/v1/semantic-routers/${saved.id}/publish`, 'POST')).data.sha256, published.sha256);
  async function startRuntime(publication) {
    const child = launch('runtime', 'java', [
      '-jar', runtimeJar, 'runtime',
      '--status-port', String(ports.status),
      '-p', `camel.component.typesafe-ai.base-url=${providerUrl}`,
      '-p', 'camel.component.typesafe-ai.request-timeout=5000',
      '-p', 'camel.component.typesafe-ai.max-concurrent-requests=4',
    ], {
      ...providerEnvironment,
      WSR_SEMANTIC_ROUTE: definition.name,
      WSR_BARN_URL: barnUrl,
      WSR_REGISTRATION_URL: management,
      WSR_NAME: 'semantic-acceptance',
      WSR_NAMESPACE: 'semantic-acceptance',
      WSR_MCP_ADDRESS: `http://127.0.0.1:${ports.wsr}/mcp`,
      WSR_MCP_PORT: String(ports.wsr),
      WSR_DATA_DIR: resolve(work, 'wsr-data'),
    });
    await ready(statusUrl, child, body => body.state === 'ready');
    const status = await json(statusUrl);
    assert.equal(status.revision, publication.revision);
    assert.equal(status.digest, publication.sha256);
    return child;
  }
  let runtime = await startRuntime(published);
  const direct = new Mcp(`http://127.0.0.1:${ports.wsr}/mcp`);
  await direct.initialize();
  assert.equal((await direct.request('tools/list')).result.tools.length, 1);
  const client = new Mcp(`http://127.0.0.1:${ports.mcp}/semantic-acceptance/mcp`);
  await client.initialize();
  const tools = (await client.request('tools/list')).result.tools;
  assert.equal(tools.length, 1);
  assert.equal(tools[0].name, 'route_support');
  assert.equal(tools[0].inputSchema.properties.message.type, 'string');
  assert(tools[0].inputSchema.required.includes('message'));
  const other = new Mcp(`http://127.0.0.1:${ports.mcp}/default/mcp`);
  await other.initialize();
  assert.equal((await other.request('tools/list')).result.tools.length, 0);
  const selectors = { namespace: 'semantic-acceptance', operation: 'tools/call', target_type: 'tool', target_name: { matcher: 'exact', value: 'route_support' } };
  const callsBeforeDeny = providerCalls;
  assert((await client.call('billing')).error, 'Explicit policy must deny before inference');
  assert.equal(providerCalls, callsBeforeDeny);
  await json(`${management}/api/v1/action-policies`, 'PUT', { policy: { rules: [{ id: 'allow-support', effect: 'allow', selectors }] } });
  for (const label of ['billing', 'technical', 'sink', 'no_match']) {
    const result = await client.call(label);
    assert(!result.error && !result.result.isError, JSON.stringify(result));
    const text = result.result.content.filter(block => block.type === 'text').map(block => block.text).join(' ');
    assert.match(text, label === 'no_match' ? /No matching action/ : new RegExp(label, 'i'));
    if (label === 'sink') assert.equal(text, 'Routed to sink.');
  }
  for (const failure of ['provider_failure', 'malformed', 'sink_failure']) {
    const result = await client.call(failure);
    assert(result.error || result.result.isError, `${failure} must be an error`);
  }
  const runtimeLog = await readFile(resolve(work, 'runtime.log'), 'utf8');
  assert(runtimeLog.includes('Sink accepted inbox: sink'), 'The native sink must receive the original message');
  assert(!runtimeLog.includes('Sink accepted inbox: sink_failure'), 'A failed sink must stop before successful delivery');
  await json(`${management}/api/v1/action-policies`, 'PUT', { policy: { rules: [{ id: 'deny-support', effect: 'deny', selectors }] } });
  const callsBeforePolicy = providerCalls;
  assert((await client.call('technical')).error);
  assert.equal(providerCalls, callsBeforePolicy, 'Denied tool must not reach the provider or action');
  definition.actions[0].configuration.prefix = 'Replacement';
  await json(`${barnUrl}/api/v1/semantic-routers/${saved.id}`, 'PUT', definition);
  const selectionUrl = `${barnUrl}/api/v1/semantic-routers/resolve?name=${encodeURIComponent(definition.name)}`;
  assert.equal((await json(selectionUrl)).data.revision, published.revision, 'Draft edits must preserve the published selection');
  const replacement = (await json(`${barnUrl}/api/v1/semantic-routers/${saved.id}/publish`, 'POST')).data;
  assert.notEqual(replacement.revision, published.revision);
  assert.notEqual(replacement.sha256, published.sha256);
  assert.equal((await json(selectionUrl)).data.revision, replacement.revision);
  assert.equal((await json(statusUrl)).revision, published.revision, 'A running WSR must retain its selected publication');
  await stop(runtime, 'SIGKILL');
  await json(`${management}/api/v1/forwards/semantic-acceptance/refreshes`, 'POST');
  assert.equal((await json(`${management}/api/v1/forwards/semantic-acceptance`)).data.available, false);
  runtime = await startRuntime(replacement);
  await json(`${management}/api/v1/action-policies`, 'PUT', { policy: { rules: [{ id: 'allow-support', effect: 'allow', selectors }] } });
  assert.match((await client.call('billing')).result.content[0].text, /Replacement/);
  await stop(runtime);
  const removed = await fetch(`${management}/api/v1/forwards/semantic-acceptance`);
  assert.equal(removed.status, 404, 'Graceful shutdown must remove its forward');
  await json(`${barnUrl}/api/v1/semantic-routers/${saved.id}`, 'DELETE');
  console.log(`PASS: remote Kamelet uploads, native Camel HTTP lookup, native sink delivery and failure, retained action pins, Barn publication, route-name discovery, environment-configured WSR, file-free preview, pinned startup, real MCP, namespace isolation, policy, branches, no-match, provider errors, replacement and shutdown. Logs: ${work}`);
  const hold = Number(process.env.SEMANTIC_ACCEPTANCE_HOLD_SECONDS ?? 0);
  if (Number.isFinite(hold) && hold > 0) await delay(Math.min(hold, 600) * 1000);
} finally {
  for (const child of processes.reverse()) await stop(child);
  await new Promise(done => provider.close(done));
}
