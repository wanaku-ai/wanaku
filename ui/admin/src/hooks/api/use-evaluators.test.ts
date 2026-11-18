import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { json, respondWith } from '../../test/browser-stub.ts';
import { ApiError } from '../../custom-fetch.ts';
import {
  activateRevision,
  evaluatorErrorKind,
  getActiveRevision,
  getRevision,
  listRevisions,
  saveEvaluators,
} from './use-evaluators.ts';

const metadata = {
  id: 1,
  created_at: '2026-08-27T10:00:00Z',
  activated_at: '2026-08-27T10:00:00Z',
  status: 'active',
  checksum: 'checksum',
  origin: 'startup',
};
const evaluators = [
  {
    name: 'gate',
    trigger: { method: 'tools/call' },
    engine: { type: 'passthrough' as const },
    processor: { path: 'a.wasm' },
  },
];
const revision = { revision: metadata, evaluators };

const rejection = async (promise: Promise<unknown>): Promise<unknown> =>
  promise.then(
    () => assert.fail('expected the request to fail'),
    (error: unknown) => error,
  );

describe('getActiveRevision', () => {
  it('returns the active revision', async () => {
    respondWith((request) => {
      assert.match(request.url, /\/api\/v1\/evaluators\/revisions\/active$/);
      return json(200, { data: revision });
    });
    assert.deepEqual(await getActiveRevision(), { state: 'active', revision });
  });

  it('maps a 404 to the absent state', async () => {
    respondWith(() => json(404, { error: 'no active revision' }));
    assert.deepEqual(await getActiveRevision(), { state: 'absent' });
  });

  it('rejects other failures', async () => {
    respondWith(() => json(500, { error: 'store unavailable' }));
    const error = await rejection(getActiveRevision());
    assert.ok(error instanceof ApiError);
    assert.equal(evaluatorErrorKind(error), 'unexpected');
  });
});

describe('listRevisions', () => {
  it('returns the metadata of every revision, including rejected revisions', async () => {
    const rejected = { ...metadata, id: 2, status: 'rejected', failure_reason: 'bad wasm' };
    respondWith(() => json(200, { data: [metadata, rejected] }));
    assert.deepEqual(await listRevisions(), [metadata, rejected]);
  });
});

describe('getRevision', () => {
  it('returns the revision with its evaluators', async () => {
    respondWith((request) => {
      assert.match(request.url, /\/api\/v1\/evaluators\/revisions\/1$/);
      return json(200, { data: revision });
    });
    assert.deepEqual(await getRevision(1), revision);
  });

  it('rejects a missing revision as not found', async () => {
    respondWith(() => json(404, { error: 'revision 9 not found' }));
    const error = await rejection(getRevision(9));
    assert.equal(evaluatorErrorKind(error), 'not_found');
  });
});

describe('saveEvaluators', () => {
  it('sends the evaluators with the expected revision', async () => {
    respondWith(async (request) => {
      assert.equal(request.method, 'PUT');
      assert.match(request.url, /\/api\/v1\/evaluators$/);
      assert.deepEqual(await request.json(), { evaluators, expected_revision: 3 });
      return json(200, { data: revision });
    });
    assert.deepEqual(await saveEvaluators(evaluators, 3), revision);
  });

  it('sends a null expected revision when no revision is active', async () => {
    respondWith(async (request) => {
      assert.deepEqual(await request.json(), { evaluators, expected_revision: null });
      return json(200, { data: revision });
    });
    await saveEvaluators(evaluators);
  });

  it('rejects a stale revision as a conflict and a rejected configuration as validation', async () => {
    respondWith(() => json(409, { error: 'expected active revision 3, but current is 4' }));
    const conflict = await rejection(saveEvaluators(evaluators, 3));
    assert.equal(evaluatorErrorKind(conflict), 'conflict');
    assert.ok(conflict instanceof Error);
    assert.equal(conflict.message, 'expected active revision 3, but current is 4');

    respondWith(() => json(422, { error: 'configuration rejected: bad wasm' }));
    assert.equal(evaluatorErrorKind(await rejection(saveEvaluators(evaluators, 3))), 'validation');
  });
});

describe('activateRevision', () => {
  it('activates a historical revision with the expected revision', async () => {
    respondWith(async (request) => {
      assert.equal(request.method, 'POST');
      assert.match(request.url, /\/api\/v1\/evaluators\/revisions\/2\/activate$/);
      assert.deepEqual(await request.json(), { expected_revision: 5 });
      return json(200, { data: revision });
    });
    assert.deepEqual(await activateRevision(2, 5), revision);
  });

  it('rejects a stale revision as a conflict', async () => {
    respondWith(() => json(409, { error: 'expected active revision 5, but current is 6' }));
    assert.equal(evaluatorErrorKind(await rejection(activateRevision(2, 5))), 'conflict');
  });
});
