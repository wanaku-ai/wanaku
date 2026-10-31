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
    ...headers,
  };
};

const REDIRECT_TS_KEY = 'wanaku_auth_redirect_ts';
const REDIRECT_LOOP_MS = 10_000;

/**
 * An unsuccessful HTTP response. `status` is the HTTP status. `data` is the `data` member of the
 * server error envelope, or `undefined` when the body is empty, is not JSON, or has no `data`.
 * `message` is the envelope `error`, a `reason_code` in `data`, or a generic status message.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly data: unknown;

  constructor(message: string, status: number, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

const parseJson = (text: string): unknown => {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
};

const toApiError = async (response: Response): Promise<ApiError> => {
  const body = parseJson(await response.text());
  const envelope =
    typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {};
  const data = envelope.data ?? undefined;
  const reason =
    typeof data === 'object' &&
    data !== null &&
    'reason_code' in data &&
    typeof data.reason_code === 'string'
      ? data.reason_code
      : undefined;
  const error = typeof envelope.error === 'string' && envelope.error ? envelope.error : undefined;
  const message = error ?? reason ?? `Request failed with status ${response.status}`;
  return new ApiError(message, response.status, data);
};

export const customFetch = async <T>(url: string, options: RequestInit): Promise<T> => {
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

  if (!response.ok) throw await toApiError(response);
  sessionStorage.removeItem(REDIRECT_TS_KEY);

  const body = await getBody<Record<string, unknown>>(response);
  const data = body?.data !== undefined ? body.data : body;

  return { status: response.status, data, headers: response.headers } as T;
};
