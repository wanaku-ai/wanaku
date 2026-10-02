#!/usr/bin/env python3
"""Exercise the built Wanaku A2A proxy against an isolated local backend.

Run: python3 tests/a2a/smoke.py --binary target/debug/wanaku-server
Uses only the Python standard library. All state is temporary.
"""
import argparse
import json
import os
import re
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


class Backend(BaseHTTPRequestHandler):
    calls = []
    canceled = False

    def log_message(self, *_):
        pass

    def reply(self, body, split=False):
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        if split:
            midpoint = len(data) // 2
            self.wfile.write(data[:midpoint])
            self.wfile.flush()
            time.sleep(0.05)
            self.wfile.write(data[midpoint:])
        else:
            self.wfile.write(data)

    def do_GET(self):
        self.calls.append(('GET', self.path))
        self.reply({'name': 'Smoke agent', 'description': 'Local test agent',
                    'url': 'http://upstream.invalid/', 'version': '1.0',
                    'protocolVersion': '0.3.0', 'capabilities': {
                        'streaming': True, 'pushNotifications': True},
                    'defaultInputModes': ['text'], 'defaultOutputModes': ['text'],
                    'skills': []}, split=True)

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        method = {'message/send': 'SendMessage', 'tasks/get': 'GetTask',
                  'tasks/cancel': 'CancelTask'}.get(request['method'], request['method'])
        self.calls.append(('POST', method))
        if method == 'CancelTask':
            type(self).canceled = True
        task = {'kind': 'task', 'id': 'smoke-task', 'contextId': 'smoke-context',
                'status': {'state': 'canceled' if self.canceled else 'working'}}
        result = {'tasks': [task]} if method == 'ListTasks' else task
        self.reply({'jsonrpc': '2.0', 'id': request['id'], 'result': result}, split=True)


class FallbackBackend(Backend):
    calls = []

    def do_POST(self):
        self.calls.append(('POST', 'unexpected-task-request'))
        self.reply({'jsonrpc': '2.0', 'id': None,
                    'error': {'code': -32001, 'message': 'Task belongs to another backend'}})


def request(url, body=None, raw=None, headers=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data,
                                 headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read())


def check(condition, name, detail=None):
    if not condition:
        raise AssertionError(f"{name}: {detail}" if detail is not None else name)
    print('PASS ' + name, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', default='target/debug/wanaku-server')
    args = parser.parse_args()
    binary = Path(args.binary).resolve()
    if not binary.is_file():
        parser.error('Build Wanaku first or provide --binary.')
    backend = ThreadingHTTPServer(('127.0.0.1', 0), Backend)
    threading.Thread(target=backend.serve_forever, daemon=True).start()
    fallback = ThreadingHTTPServer(('127.0.0.1', 0), FallbackBackend)
    threading.Thread(target=fallback.serve_forever, daemon=True).start()
    fallback_port = fallback.server_port
    upstream_port = backend.server_port
    proxy_port, mgmt_port = free_port(), free_port()
    proxy = f'http://127.0.0.1:{proxy_port}/'
    process = None
    try:
        with tempfile.TemporaryDirectory(prefix='wanaku-a2a-smoke-') as directory:
            directory = Path(directory)
            pipeline = directory / 'pipeline.yaml'
            pipeline.write_text(f'''listeners:
  - name: a2a
    address: "127.0.0.1:{proxy_port}"
    filter_chains: [a2a]
filter_chains:
  - name: a2a
    filters:
      - filter: a2a
        on_invalid: continue
        task_routing:
          enabled: true
          store: local
          ttl_seconds: 3600
          terminal_ttl_seconds: 300
        method_aliases:
          message/send: SendMessage
          tasks/get: GetTask
          tasks/cancel: CancelTask
      - filter: wanaku_a2a
        public_url: "{proxy}"
        agent: smoke-agent
        namespace: default
      - filter: wanaku_action_policy
      - filter: headers
        request_set:
          - name: Host
            value: "127.0.0.1:{upstream_port}"
      - filter: router
        routes:
          - path_prefix: "/"
            headers:
              x-praxis-a2a-route-cluster: smoke
            cluster: smoke
          - path_prefix: "/"
            headers:
              x-praxis-a2a-method: SendMessage
            cluster: smoke
          - path_prefix: "/"
            cluster: fallback
      - filter: load_balancer
        clusters:
          - name: smoke
            endpoints: ["127.0.0.1:{upstream_port}"]
          - name: fallback
            endpoints: ["127.0.0.1:{fallback_port}"]
insecure_options:
  allow_private_endpoints: true
''')
            config = directory / 'wanaku.yaml'
            config.write_text('''governance:
  default:
    mode: enforce
    no_match: deny
    on_failure: deny
    audit_level: basic
action_policy:
  rules:
    - id: allow-agent
      effect: allow
      selectors:
        target_type: agent
        target_name: {matcher: exact, value: smoke-agent}
        operation: SendMessage
      predicates:
        - operator: one_of
          pointer: /message/messageId
          values: [allowed-message, denied-message]
    - id: allow-get
      effect: allow
      selectors:
        target_type: agent
        operation: GetTask
    - id: allow-cancel
      effect: allow
      selectors:
        target_type: agent
        operation: CancelTask
    - id: deny-marked-message
      effect: deny
      selectors:
        operation: SendMessage
      predicates:
        - operator: equals
          pointer: /message/messageId
          value: denied-message
      reason_code: smoke_denied
''')
            env = {key: value for key, value in os.environ.items()
                   if not key.startswith(('WANAKU_', 'PRAXIS_'))}
            env.update(WANAKU_MGMT_LISTEN=f'127.0.0.1:{mgmt_port}',
                       WANAKU_PERSIST_BACKEND='memory', WANAKU_PERSIST_PATH=str(directory),
                       WANAKU_FORWARD_HEALTHCHECK_INTERVAL='0')
            with (directory / 'server.log').open('w') as log:
                try:
                    process = subprocess.Popen([str(binary), '--pipeline-config', str(pipeline),
                                                '--wanaku-config', str(config)],
                                               cwd=directory, env=env, stdout=log, stderr=log)
                    deadline = time.monotonic() + 30
                    while True:
                        if process.poll() is not None:
                            log.flush()
                            diagnostic = (directory / 'server.log').read_text()[-4000:]
                            diagnostic = re.sub(r'127\.0\.0\.1:\d+', '<LOCAL_ENDPOINT>', diagnostic)
                            diagnostic = diagnostic.replace(str(directory), '<TEMP_DIRECTORY>')
                            print(diagnostic, flush=True)
                            raise AssertionError('Wanaku exited during startup; see sanitized log above.')
                        try:
                            status, card = request(proxy + '.well-known/agent-card.json')
                            break
                        except (OSError, ValueError):
                            if time.monotonic() > deadline:
                                raise AssertionError('Wanaku startup timed out') from None
                            time.sleep(0.1)
                    check(status == 200 and card.get('url') == proxy, 'discovery rewrites public URL',
                          {'status': status, 'card': card})
                    check(not card['capabilities'].get('streaming') and
                          not card['capabilities'].get('pushNotifications'),
                          'discovery disables streaming and push')

                    sequence = 0
                    def rpc(method, params):
                        nonlocal sequence
                        sequence += 1
                        return request(proxy, {'jsonrpc': '2.0', 'id': sequence,
                                               'method': method, 'params': params})

                    message = {'message': {'kind': 'message', 'role': 'user',
                               'messageId': 'allowed-message',
                               'parts': [{'kind': 'text', 'text': 'Smoke test'}]}}
                    status, result = rpc('message/send', message)
                    check(status == 200 and result.get('result', {}).get('id') == 'smoke-task',
                          'SendMessage reaches upstream')
                    status, result = rpc('tasks/get', {'id': 'smoke-task'})
                    check(status == 200 and result['result']['status']['state'] == 'working',
                          'GetTask reads upstream task')
                    status, result = rpc('tasks/cancel', {'id': 'smoke-task'})
                    check(status == 200 and result['result']['status']['state'] == 'canceled',
                          'CancelTask changes upstream state')
                    status, result = rpc('tasks/get', {'id': 'smoke-task'})
                    check(result['result']['status']['state'] == 'canceled',
                          'cancellation persists in upstream task state')
                    check(not any(method == 'POST' for method, _ in FallbackBackend.calls),
                          'split responses preserve task-owner routing to original backend')

                    before = (len(Backend.calls) + len(FallbackBackend.calls))
                    denied = json.loads(json.dumps(message))
                    denied['message']['messageId'] = 'denied-message'
                    status, result = rpc('SendMessage', denied)
                    check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                          'policy deny prevents upstream dispatch')
                    unmatched = json.loads(json.dumps(message))
                    unmatched['message']['messageId'] = 'unmatched-message'
                    status, result = rpc('message/send', unmatched)
                    check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                          'no matching rule denies upstream dispatch')
                    for config_field in ('stream', 'streaming', 'pushNotificationConfig'):
                        configured = json.loads(json.dumps(message))
                        configured['configuration'] = {config_field: True if config_field != 'pushNotificationConfig' else {'url': 'https://example.invalid/'}}
                        status, result = rpc('message/send', configured)
                        check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                              config_field + ' rejected before upstream dispatch')
                    status, result = request(proxy, {'jsonrpc': '2.0', 'id': 'version-check',
                                                    'method': 'message/send', 'params': message},
                                             headers={'A2A-Version': '1.0'})
                    check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                          'unsupported protocol version rejected before upstream dispatch')
                    for method in ('ListTasks', 'SendStreamingMessage', 'SubscribeToTask',
                                   'CreateTaskPushNotificationConfig', 'GetTaskPushNotificationConfig',
                                   'ListTaskPushNotificationConfigs', 'DeleteTaskPushNotificationConfig',
                                   'UnknownMethod'):
                        status, result = rpc(method, {'id': 'smoke-task'})
                        check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                              method + ' rejected before upstream dispatch')
                    status, result = request(proxy, raw=b'')
                    check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                          'empty POST rejected before upstream dispatch')
                    status, result = request(proxy + 'unsupported')
                    check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                          'unsupported GET path rejected before upstream dispatch')
                    status, result = request(proxy, raw=b'{invalid')
                    check('error' in result and (len(Backend.calls) + len(FallbackBackend.calls)) == before,
                          'malformed JSON rejected before upstream dispatch')
                    status, result = request(f'http://127.0.0.1:{mgmt_port}/api/v1/audit/events?limit=100')
                    events = result['data']['events']
                    a2a = [event for event in events if event['protocol'] == 'a2a']
                    check(status == 200 and any(event['operation'] == 'SendMessage' and
                          event['decision'] == 'block' for event in a2a),
                          'audit records A2A policy denial')
                    check(any(event.get('request_id') and event.get('correlation_id')
                              for event in a2a), 'audit records request correlation')
                except Exception:
                    log.flush()
                    diagnostic = (directory / 'server.log').read_text()[-8000:]
                    diagnostic = re.sub(r'127\.0\.0\.1:\d+', '<LOCAL_ENDPOINT>', diagnostic)
                    diagnostic = diagnostic.replace(str(directory), '<TEMP_DIRECTORY>')
                    print('Wanaku log tail:\n' + diagnostic, flush=True)
                    raise

    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        backend.shutdown()
        backend.server_close()
        fallback.shutdown()
        fallback.server_close()


if __name__ == '__main__':
    main()
