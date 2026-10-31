// Browser globals for `node --test`. Vite defines `VITE_API_URL` at build time, and the
// browser provides `window` and `sessionStorage`.

type FetchHandler = (request: Request) => Response | Promise<Response>;

export const browser = {
  reloads: 0,
  storage: new Map<string, string>(),
  handler: (() => new Response(null, { status: 204 })) as FetchHandler,
};

Object.assign(globalThis, {
  VITE_API_URL: '',
  window: {
    location: {
      origin: 'http://wanaku.test',
      reload: () => {
        browser.reloads += 1;
      },
    },
  },
  sessionStorage: {
    getItem: (key: string) => browser.storage.get(key) ?? null,
    setItem: (key: string, value: string) => browser.storage.set(key, value),
    removeItem: (key: string) => browser.storage.delete(key),
  },
  fetch: (request: Request) => browser.handler(request),
});

/** Resets the stub and answers every request with `handler`. */
export const respondWith = (handler: FetchHandler) => {
  browser.reloads = 0;
  browser.storage.clear();
  browser.handler = handler;
};

export const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
