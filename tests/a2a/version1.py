#!/usr/bin/env python3
"""Verify A2A 1.0 JSON-RPC and legacy compatibility against isolated backends.

Run: python3 tests/a2a/version1.py --binary target/debug/wanaku-server
Uses the existing standard-library smoke fixtures. All state is temporary.
"""
import argparse
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import threading
import time
from http.server import ThreadingHTTPServer

sys.dont_write_bytecode = True
from smoke import Backend, check, check_rejected_card, free_port, request
from managed import stop


def backend_handler(label):
    class VersionBackend(Backend):
        calls = []
        canceled = False

        def do_GET(self):
            self.calls.append(('GET', self.path))
            origin = f'http://127.0.0.1:{self.server.server_port}'
            interface = {'url': origin + '/rpc', 'protocolBinding': 'JSONRPC',
                         'protocolVersion': '1.0', 'tenant': 'tenant-a'}
            card = {'name': label, 'version': '1.0.0',
                    'capabilities': {'streaming': True, 'pushNotifications': True,
                                     'extendedAgentCard': True},
                    'skills': [], 'defaultInputModes': ['text/plain'],
                    'defaultOutputModes': ['text/plain'], 'url': origin + '/rpc',
                    'additionalInterfaces': [{'url': origin + '/bypass', 'transport': 'JSONRPC'}],
                    'signatures': [{}]}
            if self.path == '/cards/legacy':
                card.update(protocolVersion='0.3.0', url=origin + '/rpc',
                            preferredTransport='JSONRPC')
            elif self.path != '/cards/missing':
                card['supportedInterfaces'] = [interface]
                if self.path == '/cards/mixed':
                    card['supportedInterfaces'] += [dict(interface, url=origin + '/different'),
                                                     dict(interface, protocolBinding='HTTP+JSON')]
                elif self.path == '/cards/rest':
                    card['supportedInterfaces'] = [dict(interface, protocolBinding='HTTP+JSON')]
                elif self.path == '/cards/future':
                    card['supportedInterfaces'] = [dict(interface, protocolVersion='9.0')]
            self.reply(card, split=True)

        def do_POST(self):
            raw = self.rfile.read(int(self.headers['Content-Length']))
            payload = json.loads(raw)
            self.calls.append(('POST', self.path, payload, self.headers.get('A2A-Version'), raw))
            if payload['method'] == 'CancelTask':
                type(self).canceled = True
            task = {'id': 'same-task', 'contextId': 'same-context',
                    'status': {'state': 'TASK_STATE_CANCELED' if self.canceled else 'TASK_STATE_COMPLETED'},
                    'artifacts': [{'artifactId': label, 'parts': [{'text': 'echo'}]}]}
            result = {'task': task} if payload['method'] == 'SendMessage' else task
            self.reply({'jsonrpc': '2.0', 'id': payload['id'], 'result': result}, split=True)
    return VersionBackend


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', default='target/debug/wanaku-server')
    args = parser.parse_args()
    binary = Path(args.binary).resolve()
    if not binary.is_file():
        parser.error('Build Wanaku first or provide --binary.')
    servers = []
    handlers = []
    process = None
    try:
        for label in ('first', 'second'):
            handler = backend_handler(label)
            server = ThreadingHTTPServer(('127.0.0.1', 0), handler)
            threading.Thread(target=server.serve_forever, daemon=True).start()
            servers.append(server)
            handlers.append(handler)
        proxy_port, mgmt_port = free_port(), free_port()
        proxy = f'http://127.0.0.1:{proxy_port}'
        api = f'http://127.0.0.1:{mgmt_port}/api/v1'
        with tempfile.TemporaryDirectory(prefix='wanaku-a2a-version1-') as temporary:
            directory = Path(temporary)
            (directory / 'pipeline.yaml').write_text(f'''listeners:
  - name: a2a
    address: "127.0.0.1:{proxy_port}"
    filter_chains: [a2a]
filter_chains:
  - name: a2a
    filters:
      - filter: a2a
        on_invalid: continue
        method_aliases:
          message/send: SendMessage
          tasks/get: GetTask
          tasks/cancel: CancelTask
      - filter: wanaku_a2a
        managed: true
        public_url: "{proxy}/"
      - filter: wanaku_action_policy
insecure_options:
  allow_private_endpoints: true
  allow_private_upstreams: true
''')
            (directory / 'wanaku.yaml').write_text('''governance:
  default:
    mode: enforce
    no_match: deny
    on_failure: deny
    audit_level: basic
action_policy:
  rules:
    - id: allow-version1-agent
      effect: allow
      selectors:
        target_type: agent
        target_name: {matcher: exact, value: version1-agent}
    - id: deny-marked-message
      effect: deny
      selectors:
        operation: SendMessage
      predicates:
        - operator: equals
          pointer: /message/messageId
          value: denied-message
''')
            env = {key: value for key, value in os.environ.items()
                   if not key.startswith(('WANAKU_', 'PRAXIS_'))}
            env.update(WANAKU_MGMT_LISTEN=f'127.0.0.1:{mgmt_port}',
                       WANAKU_A2A_PUBLIC_URL=proxy, WANAKU_PERSIST_BACKEND='memory',
                       WANAKU_PERSIST_PATH=str(directory), WANAKU_FORWARD_HEALTHCHECK_INTERVAL='0')
            with (directory / 'server.log').open('w') as log:
                try:
                    process = subprocess.Popen([str(binary), '--pipeline-config', str(directory / 'pipeline.yaml'),
                        '--wanaku-config', str(directory / 'wanaku.yaml')], cwd=directory,
                        env=env, stdout=log, stderr=log)
                    deadline = time.monotonic() + 30
                    while time.monotonic() < deadline:
                        if process.poll() is not None:
                            raise AssertionError('Wanaku exited during startup')
                        try:
                            if request(api + '/agents')[0] == 200:
                                break
                        except (OSError, ValueError):
                            pass
                        time.sleep(0.1)
                    else:
                        raise AssertionError('Wanaku startup timed out')

                    def register(namespace, server, card='complete'):
                        entry = {'name': 'version1-agent', 'namespace': namespace,
                                 'address': f'http://127.0.0.1:{server.server_port}/rpc',
                                 'cardAddress': f'http://127.0.0.1:{server.server_port}/cards/{card}'}
                        status, result = request(api + '/agents', entry)
                        check(status in (200, 201), 'registers isolated ' + namespace + ' agent', result)
                        return entry

                    entry = register('default', servers[0])
                    register('other', servers[1])
                    endpoint = proxy + '/default/a2a/version1-agent'
                    count = lambda: sum(len(handler.calls) for handler in handlers)
                    sequence = 0

                    def rpc(method, params, version='1.0', namespace='default'):
                        nonlocal sequence
                        sequence += 1
                        body = {'jsonrpc': '2.0', 'id': f'version1-{sequence}',
                                'method': method, 'params': params}
                        headers = {} if version is None else {'A2A-Version': version}
                        status, result = request(proxy + f'/{namespace}/a2a/version1-agent', body, headers=headers)
                        return status, result, body

                    message = {'message': {'messageId': 'allowed-message', 'role': 'ROLE_USER',
                                          'parts': [{'text': 'echo'}]}}
                    status, result, sent = rpc('SendMessage', message)
                    expected = {'jsonrpc': '2.0', 'id': sent['id'], 'result': {'task': {
                        'id': 'same-task', 'contextId': 'same-context',
                        'status': {'state': 'TASK_STATE_COMPLETED'},
                        'artifacts': [{'artifactId': 'first', 'parts': [{'text': 'echo'}]}]}}}
                    check(status == 200 and result == expected,
                          '1.0 SendMessage preserves complete task-wrapped response', result)
                    call = handlers[0].calls[-1]
                    check(call[1] == '/rpc' and call[2] == sent and call[3] == '1.0' and
                          call[4] == json.dumps(sent).encode(), 'forwards 1.0 header and request bytes unchanged')
                    check(result['result']['task']['status']['state'] == 'TASK_STATE_COMPLETED',
                          'forwards ProtoJSON task state unchanged')
                    agent_message = copy.deepcopy(message)
                    agent_message['message']['role'] = 'ROLE_AGENT'
                    status, result, _ = rpc('SendMessage', agent_message)
                    check(status == 200 and 'task' in result.get('result', {}), 'accepts 1.0 agent role', result)
                    status, result, _ = rpc('GetTask', {'id': 'same-task'})
                    check(status == 200 and result.get('result', {}).get('id') == 'same-task', '1.0 GetTask reaches owner', result)
                    status, result, _ = rpc('CancelTask', {'id': 'same-task'})
                    check(status == 200 and result.get('result', {}).get('status', {}).get('state') == 'TASK_STATE_CANCELED',
                          '1.0 CancelTask forwards canceled task', result)
                    status, result, _ = rpc('GetTask', {'id': 'same-task'}, namespace='other')
                    check(result.get('result', {}).get('artifacts', [{}])[0].get('artifactId') == 'second',
                          'identical task IDs remain isolated by namespace', result)

                    for card_kind in ('complete', 'mixed', 'missing', 'legacy', 'rest', 'future'):
                        entry['cardAddress'] = f'http://127.0.0.1:{servers[0].server_port}/cards/{card_kind}'
                        request(api + '/agents/default/version1-agent', entry, method='PUT')
                        if card_kind in ('rest', 'future'):
                            check_rejected_card(endpoint + '/.well-known/agent-card.json',
                                                card_kind + ' discovery rejects unsupported interfaces')
                            continue
                        status, card = request(endpoint + '/.well-known/agent-card.json')
                        if card_kind == 'legacy':
                            check(status == 200 and card.get('url') == endpoint and
                                  card.get('protocolVersion') == '0.3.0', 'explicit 0.3 card retains legacy shape', card)
                        else:
                            interfaces = card.get('supportedInterfaces', [])
                            check(status == 200 and len(interfaces) == 1 and
                                  interfaces[0].get('url') == endpoint and
                                  interfaces[0].get('protocolBinding') == 'JSONRPC' and
                                  interfaces[0].get('protocolVersion') == '1.0',
                                  card_kind + ' discovery advertises only usable proxy interface', card)
                            check(all(field not in card for field in ('url', 'additionalInterfaces', 'signatures')),
                                  card_kind + ' discovery removes upstream bypass fields and signatures')
                            if card_kind != 'missing':
                                check(interfaces[0].get('tenant') == 'tenant-a', card_kind + ' discovery preserves tenant')
                            check(not card.get('capabilities', {}).get('streaming') and
                                  not card.get('capabilities', {}).get('pushNotifications') and
                                  not card.get('capabilities', {}).get('extendedAgentCard'),
                                  card_kind + ' discovery disables unsupported capabilities', card)

                    legacy = {'message': {'messageId': 'legacy-message', 'role': 'user',
                                          'parts': [{'kind': 'text', 'text': 'echo'}]}}
                    for version in (None, '', '0.3', '0.3.0'):
                        status, result, _ = rpc('message/send', legacy, version=version)
                        check(status == 200 and 'result' in result, 'legacy version selection ' + repr(version), result)
                    before = count()
                    denied = copy.deepcopy(message)
                    denied['message']['messageId'] = 'denied-message'
                    _, result, _ = rpc('SendMessage', denied)
                    check('error' in result and count() == before, '1.0 policy denial prevents backend dispatch', result)
                    for version in ('9.0', '1.0.0', 'garbage'):
                        _, result, _ = rpc('SendMessage', message, version=version)
                        check(result.get('error', {}).get('code') == -32009 and count() == before,
                              'unsupported version ' + version + ' rejected before dispatch', result)
                    malformed = [dict(message, configuration={'taskPushNotificationConfig': {'url': 'https://example.invalid'}}),
                                 dict(message, configuration={'stream': True}),
                                 {'message': {'messageId': 'bad', 'role': 'user', 'parts': [{'text': 'echo'}]}},
                                 {'message': {'messageId': 'bad', 'role': 'ROLE_USER', 'parts': []}},
                                 {'message': {'messageId': 'bad', 'role': 'ROLE_USER',
                                              'parts': [{'text': 'echo', 'data': {}}]}}]
                    for index, params in enumerate(malformed):
                        _, result, _ = rpc('SendMessage', params)
                        check('error' in result and count() == before, f'invalid 1.0 params {index} rejected before dispatch', result)
                    for method in ('SendStreamingMessage', 'SubscribeToTask', 'CreateTaskPushNotificationConfig',
                                   'GetTaskPushNotificationConfig', 'ListTaskPushNotificationConfigs',
                                   'DeleteTaskPushNotificationConfig'):
                        _, result, _ = rpc(method, {'id': 'same-task'})
                        check('error' in result and count() == before, method + ' rejected before dispatch', result)
                    for method, params in [('message/send', message), ('GetTask', {'name': 'same-task'}),
                                           ('CancelTask', {'id': ''})]:
                        _, result, _ = rpc(method, params)
                        check('error' in result and count() == before, method + ' invalid shape rejected before dispatch', result)
                    _, result = request(endpoint, raw=b'{broken', headers={'A2A-Version': '1.0'})
                    check('error' in result and count() == before, 'malformed 1.0 JSON rejected before dispatch', result)
                    status, audit = request(api + '/audit/events?limit=100')
                    denied_events = [event for event in audit['data']['events']
                                     if event.get('protocol') == 'a2a' and event.get('operation') == 'SendMessage'
                                     and event.get('decision') == 'block']
                    check(status == 200 and any(event.get('request_id') and event.get('correlation_id')
                                               for event in denied_events), '1.0 policy denial audit includes correlation')
                except Exception:
                    log.flush()
                    diagnostic = (directory / 'server.log').read_text()[-8000:]
                    diagnostic = re.sub(r'127\.0\.0\.1:\d+', '<LOCAL_ENDPOINT>', diagnostic)
                    print(diagnostic.replace(str(directory), '<TEMP_DIRECTORY>'), flush=True)
                    raise
    finally:
        stop(process)
        for server in servers:
            server.shutdown()
            server.server_close()


if __name__ == '__main__':
    main()
