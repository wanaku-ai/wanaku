const getBody = <T>(c: Response | Request): Promise<T> => {
    const contentType = c.headers.get('content-type');

    if (contentType && contentType.includes('application/json')) {
      return c.json();
    }

    return c.text() as Promise<T>;
  };

  // NOTE: Update just base url
  export const getUrl = (contextUrl: string): string => {
    const baseUrl = VITE_API_URL || window.location.origin;
    const url = new URL(contextUrl, baseUrl);
    const pathname = url.pathname;
    const search = url.search;

    const requestUrl = new URL(`${baseUrl}${pathname}${search}`);

    return requestUrl.toString();
  };

  // NOTE: Add headers
  const getHeaders = (headers?: HeadersInit): HeadersInit => {
    return {
      ...headers
    };
  };

  const REDIRECT_TS_KEY = 'wanaku_auth_redirect_ts';
  const REDIRECT_LOOP_MS = 10_000;

  export const customFetch = async <T>(
    url: string,
    options: RequestInit,
  ): Promise<T> => {
    const requestUrl = getUrl(url);
    const requestHeaders = getHeaders(options.headers);

    const requestInit: RequestInit = {
      ...options,
      headers: requestHeaders,
      redirect: 'manual',
    };

    const request = new Request(requestUrl, requestInit);
    const response = await fetch(request);

    if (response.type === 'opaqueredirect' || response.status === 401) {
      const lastRedirect = Number(sessionStorage.getItem(REDIRECT_TS_KEY) || '0');
      if (Date.now() - lastRedirect < REDIRECT_LOOP_MS) {
        throw new Error('Authentication redirect loop detected — check OIDC configuration');
      }
      sessionStorage.setItem(REDIRECT_TS_KEY, String(Date.now()));
      window.location.reload();
      throw new Error('Redirecting to login');
    }

    if (response.ok) {
      sessionStorage.removeItem(REDIRECT_TS_KEY);
    }

    const data = await getBody<T>(response);

    return { status: response.status, data, headers: response.headers } as T;
  };
