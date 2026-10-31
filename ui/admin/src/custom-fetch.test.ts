import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { browser, json, respondWith } from './test/browser-stub.ts';
import { ApiError, customFetch } from './custom-fetch.ts';

const REDIRECT_TS_KEY = 'wanaku_auth_redirect_ts';

const failure = async (status: number, body: BodyInit | null, contentType?: string) => {
  respondWith(
    () =>
      new Response(body, {
        status,
        headers: contentType ? { 'content-type': contentType } : undefined,
      }),
  );
  return customFetch('/api/v1/test', { method: 'GET' }).then(
    () => assert.fail('expected the request to fail'),
    (error: unknown) => {
      assert.ok(error instanceof ApiError);
      assert.ok(error instanceof Error);
      return error;
    },
  );
};

describe('customFetch', () => {
  beforeEach(() => respondWith(() => json(200, { data: null, error: null })));

  it('unwraps the data envelope of a successful response', async () => {
    respondWith(() => json(200, { data: [{ name: 'tool' }], error: null }));
    const response = await customFetch<{ status: number; data: unknown }>('/api/v1/tools', {
      method: 'GET',
    });
    assert.equal(response.status, 200);
    assert.deepEqual(response.data, [{ name: 'tool' }]);
  });

  it('keeps the status and message of a 404 response', async () => {
    const error = await failure(
      404,
      JSON.stringify({ data: null, error: 'revision 7 not found' }),
      'application/json',
    );
    assert.equal(error.status, 404);
    assert.equal(error.message, 'revision 7 not found');
    assert.equal(error.data, undefined);
    assert.equal(error.name, 'ApiError');
  });

  it('keeps the status and message of a 409 response', async () => {
    const error = await failure(
      409,
      JSON.stringify({ error: 'expected active revision 2, but current is 3' }),
      'application/json',
    );
    assert.equal(error.status, 409);
    assert.equal(error.message, 'expected active revision 2, but current is 3');
  });

  it('keeps the structured data of a 422 response', async () => {
    const data = { reason_code: 'candidate_invalid', validation: { valid: false } };
    const error = await failure(
      422,
      JSON.stringify({ data, error: 'configuration rejected' }),
      'application/json',
    );
    assert.equal(error.status, 422);
    assert.equal(error.message, 'configuration rejected');
    assert.deepEqual(error.data, data);
  });

  it('uses the reason code when the envelope has no error message', async () => {
    const error = await failure(
      422,
      JSON.stringify({ data: { reason_code: 'active_configuration_invalid' }, error: null }),
      'application/json',
    );
    assert.equal(error.message, 'active_configuration_invalid');
  });

  it('keeps the status of a 500 response with a plain text body', async () => {
    const error = await failure(500, 'Internal Server Error', 'text/plain');
    assert.equal(error.status, 500);
    assert.equal(error.message, 'Request failed with status 500');
    assert.equal(error.data, undefined);
  });

  it('keeps the status of a response with a malformed JSON body', async () => {
    const error = await failure(502, '{"error": "trunc', 'application/json');
    assert.equal(error.status, 502);
    assert.equal(error.message, 'Request failed with status 502');
    assert.equal(error.data, undefined);
  });

  it('keeps the status of a response with an empty body', async () => {
    const error = await failure(503, null);
    assert.equal(error.status, 503);
    assert.equal(error.message, 'Request failed with status 503');
  });

  it('ignores a JSON body that is not an error envelope', async () => {
    const error = await failure(500, JSON.stringify(['unexpected']), 'application/json');
    assert.equal(error.status, 500);
    assert.equal(error.message, 'Request failed with status 500');
    assert.equal(error.data, undefined);
  });
});

describe('customFetch authentication', () => {
  it('reloads the page once on a 401 response', async () => {
    respondWith(() => json(401, { error: 'unauthorized' }));
    await assert.rejects(customFetch('/api/v1/tools', { method: 'GET' }), {
      message: 'Redirecting to login',
    });
    assert.equal(browser.reloads, 1);
    assert.ok(browser.storage.has(REDIRECT_TS_KEY));
  });

  it('reloads the page on a redirect to the login page', async () => {
    respondWith(() => ({ type: 'opaqueredirect', status: 0, ok: false }) as Response);
    await assert.rejects(customFetch('/api/v1/tools', { method: 'GET' }), {
      message: 'Redirecting to login',
    });
    assert.equal(browser.reloads, 1);
  });

  it('stops a redirect loop without a second reload', async () => {
    respondWith(() => json(401, { error: 'unauthorized' }));
    await assert.rejects(customFetch('/api/v1/tools', { method: 'GET' }));
    await assert.rejects(customFetch('/api/v1/tools', { method: 'GET' }), (error: unknown) => {
      assert.ok(!(error instanceof ApiError));
      assert.match((error as Error).message, /Authentication redirect loop detected/);
      return true;
    });
    assert.equal(browser.reloads, 1);
  });

  it('clears the redirect marker after a successful response', async () => {
    respondWith(() => json(200, { data: [] }));
    browser.storage.set(REDIRECT_TS_KEY, String(Date.now()));
    await customFetch('/api/v1/tools', { method: 'GET' });
    assert.equal(browser.storage.has(REDIRECT_TS_KEY), false);
  });

  it('keeps the redirect marker after an ordinary failure', async () => {
    respondWith(() => json(500, { error: 'boom' }));
    browser.storage.set(REDIRECT_TS_KEY, '1');
    await assert.rejects(customFetch('/api/v1/tools', { method: 'GET' }), ApiError);
    assert.equal(browser.storage.get(REDIRECT_TS_KEY), '1');
  });
});
