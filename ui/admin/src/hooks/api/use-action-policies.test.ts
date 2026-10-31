import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { json, respondWith } from '../../test/browser-stub.ts';
import { ApiError } from '../../custom-fetch.ts';
import { actionPolicyErrorKind, getActivePolicy, getRevision } from './use-action-policies.ts';

const revision = {
  revision: {
    id: 1,
    created_at: '2026-08-27T10:00:00Z',
    activated_at: '2026-08-27T10:00:00Z',
    status: 'active',
    checksum: 'checksum',
    origin: 'startup',
  },
  policy: { rules: [] },
};

const rejection = async (promise: Promise<unknown>): Promise<unknown> =>
  promise.then(
    () => assert.fail('expected the request to fail'),
    (error: unknown) => error,
  );

describe('getActivePolicy', () => {
  it('returns the active revision', async () => {
    respondWith(() => json(200, { data: revision }));
    assert.deepEqual(await getActivePolicy(), { state: 'active', revision });
  });

  it('maps a 404 from the active-policy endpoint to the absent state', async () => {
    respondWith(() => json(404, { error: 'any wording' }));
    assert.deepEqual(await getActivePolicy(), { state: 'absent' });
  });

  it('maps a 404 with a malformed body to the absent state', async () => {
    respondWith(() => new Response('not json', { status: 404 }));
    assert.deepEqual(await getActivePolicy(), { state: 'absent' });
  });

  it('rejects other failures', async () => {
    respondWith(() => json(500, { error: 'store unavailable' }));
    const error = await rejection(getActivePolicy());
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 500);
    assert.equal(error.message, 'store unavailable');
    assert.equal(actionPolicyErrorKind(error), 'unexpected');
  });
});

describe('getRevision', () => {
  it('returns the revision', async () => {
    respondWith((request) => {
      assert.match(request.url, /\/api\/v1\/action-policies\/revisions\/1$/);
      return json(200, { data: revision });
    });
    assert.deepEqual(await getRevision(1), revision);
  });

  it('keeps a missing revision distinct from an absent active policy', async () => {
    respondWith(() => json(404, { error: 'revision 9 not found' }));
    const error = await rejection(getRevision(9));
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 404);
    assert.equal(error.message, 'revision 9 not found');
    assert.equal(actionPolicyErrorKind(error), 'not_found');
  });
});

describe('actionPolicyErrorKind', () => {
  it('classifies failures by HTTP status', () => {
    assert.equal(actionPolicyErrorKind(new ApiError('bad', 400)), 'invalid_request');
    assert.equal(actionPolicyErrorKind(new ApiError('missing', 404)), 'not_found');
    assert.equal(actionPolicyErrorKind(new ApiError('stale', 409)), 'conflict');
    assert.equal(actionPolicyErrorKind(new ApiError('rejected', 422)), 'validation');
    assert.equal(actionPolicyErrorKind(new ApiError('failed', 500)), 'unexpected');
  });

  it('does not use the error message', () => {
    assert.equal(actionPolicyErrorKind(new ApiError('revision 1 not found', 500)), 'unexpected');
    assert.equal(actionPolicyErrorKind(new Error('conflict')), 'unexpected');
    assert.equal(actionPolicyErrorKind('conflict'), 'unexpected');
  });

  it('classifies 409 and 422 responses from the API', async () => {
    respondWith(() => json(409, { error: 'expected active revision 1, but current is 2' }));
    assert.equal(actionPolicyErrorKind(await rejection(getRevision(1))), 'conflict');
    respondWith(() => json(422, { error: 'configuration rejected: empty selectors' }));
    assert.equal(actionPolicyErrorKind(await rejection(getRevision(1))), 'validation');
  });
});
