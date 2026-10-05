#!/usr/bin/env python3
"""Test management CRUD, live A2A forwarding, namespace isolation and restart.

Run: python3 tests/a2a/managed.py --binary target/debug/wanaku-server
Uses the repository default pipeline with isolated listener ports and data.
"""
import argparse
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

from smoke import Backend, check, free_port, request


def handler(label):
    class AgentBackend(Backend):
        calls = []
        canceled = False

        def reply(self, body, split=False):
            if isinstance(body.get('result'), dict):
                body['result']['backend'] = label
            super().reply(body, split=split)
    return AgentBackend


def stop(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', default='target/debug/wanaku-server')
    args = parser.parse_args()
    binary = Path(args.binary).resolve()
    if not binary.is_file():
        parser.error('Build Wanaku first or provide --binary.')
    root = Path(__file__).resolve().parents[2]
    servers, handlers = [], []
    for label in ('first', 'second'):
        backend = handler(label)
        server = ThreadingHTTPServer(('127.0.0.1', 0), backend)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        servers.append(server)
        handlers.append(backend)
    ports = {port: free_port() for port in (8080, 8081, 8083, 8084)}
    proxy = f'http://127.0.0.1:{ports[8084]}'
    api = f'http://127.0.0.1:{ports[8080]}/api/v1/agents'
    process = None
    try:
        with tempfile.TemporaryDirectory(prefix='wanaku-a2a-managed-') as directory:
            directory = Path(directory)
            pipeline = (root / 'server/src/default.yaml').read_text()
            for original, actual in ports.items():
                pipeline = pipeline.replace(f'0.0.0.0:{original}', f'127.0.0.1:{actual}')
            pipeline = pipeline.replace('http://127.0.0.1:8084/', proxy + '/')
            pipeline = pipeline.replace('  skip_pipeline_validation: true\n', '')
            if '  allow_private_upstreams: true' not in pipeline:
                pipeline += '  allow_private_upstreams: true\n'
            (directory / 'pipeline.yaml').write_text(pipeline)
            (directory / 'wanaku.yaml').write_text('''governance:
  default:
    mode: enforce
    no_match: deny
    on_failure: deny
    audit_level: basic
action_policy:
  rules:
    - id: allow-registered-agent
      effect: allow
      selectors:
        target_type: agent
        target_name: {matcher: exact, value: smoke-agent}
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
            env.update(WANAKU_MGMT_LISTEN=f'127.0.0.1:{ports[8080]}',
                       WANAKU_A2A_PUBLIC_URL=proxy,
                       WANAKU_PERSIST_BACKEND='file',
                       WANAKU_PERSIST_PATH=str(directory / 'data'),
                       WANAKU_FORWARD_HEALTHCHECK_INTERVAL='0')
            with (directory / 'server.log').open('w') as log:
                def start():
                    running = subprocess.Popen([str(binary), '--pipeline-config',
                        str(directory / 'pipeline.yaml'), '--wanaku-config',
                        str(directory / 'wanaku.yaml')], cwd=directory, env=env,
                        stdout=log, stderr=log)
                    deadline = time.monotonic() + 30
                    while time.monotonic() < deadline:
                        if running.poll() is not None:
                            raise AssertionError('Wanaku exited during startup')
                        try:
                            status, _ = request(api)
                            if status == 200:
                                return running
                        except (OSError, ValueError):
                            pass
                        time.sleep(0.1)
                    stop(running)
                    raise AssertionError('Management API startup timed out')

                def count():
                    return sum(len(backend.calls) for backend in handlers)

                def rpc(namespace='default', name='smoke-agent', message_id='allowed-message'):
                    return request(f'{proxy}/a2a/{namespace}/{name}', {
                        'jsonrpc': '2.0', 'id': message_id, 'method': 'message/send',
                        'params': {'message': {'kind': 'message', 'role': 'user',
                            'messageId': message_id,
                            'parts': [{'kind': 'text', 'text': 'Managed smoke'}]}}})

                try:
                    process = start()
                    entry = {'name': 'smoke-agent', 'namespace': 'default',
                             'description': 'Managed smoke',
                             'address': f'http://localhost:{servers[0].server_port}/'}
                    status, result = request(api, entry)
                    check(status in (200, 201), 'creates managed A2A agent', result)
                    expected_proxy = f'{proxy}/a2a/default/smoke-agent'
                    status, result = request(api + '/default/smoke-agent')
                    check(status == 200 and result['data']['proxyUrl'] == expected_proxy,
                          'management returns agent proxy URL', result)
                    status, card = request(expected_proxy + '/.well-known/agent-card.json')
                    check(status == 200 and card['url'] == expected_proxy,
                          'managed discovery rewrites full proxy URL', card)
                    status, result = rpc()
                    check(status == 200 and result.get('result', {}).get('backend') == 'first',
                          'registered agent forwards to configured backend', result)
                    before = count()
                    status, result = rpc(message_id='denied-message')
                    check('error' in result and count() == before,
                          'managed policy deny prevents backend dispatch', result)
                    entry['address'] = f'http://127.0.0.1:{servers[1].server_port}/'
                    status, result = request(api + '/default/smoke-agent', entry, method='PUT')
                    check(status == 200, 'updates managed agent', result)
                    status, result = rpc(message_id='edited-message')
                    check(result.get('result', {}).get('backend') == 'second',
                          'address edit changes live routing without restart', result)
                    second_namespace = dict(entry, namespace='other',
                                            address=f'http://127.0.0.1:{servers[0].server_port}/')
                    status, result = request(api, second_namespace)
                    check(status in (200, 201), 'registers same name in another namespace', result)
                    status, result = request(api + '?namespace=other')
                    check(status == 200 and len(result['data']) == 1 and
                          result['data'][0]['namespace'] == 'other',
                          'management namespace filter isolates agent list', result)
                    status, result = rpc(namespace='other', message_id='other-message')
                    check(result.get('result', {}).get('backend') == 'first',
                          'namespace isolates same-name agent routing', result)
                    stop(process)
                    process = start()
                    status, result = rpc(message_id='restart-message')
                    check(result.get('result', {}).get('backend') == 'second',
                          'agent configuration persists across restart', result)
                    status, result = request(api + '/default/smoke-agent', method='DELETE')
                    check(status in (200, 204), 'deletes managed agent', result)
                    before = count()
                    status, result = rpc(message_id='deleted-message')
                    check(status == 404 and count() == before,
                          'deleted agent returns 404 without backend dispatch', result)
                    status, result = rpc(namespace='other', message_id='surviving-message')
                    check(result.get('result', {}).get('backend') == 'first',
                          'deletion preserves other namespace agent', result)
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
