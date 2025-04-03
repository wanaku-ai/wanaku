const getBody = <T>(c: Response | Request): Promise<T> => {
    const contentType = c.headers.get('content-type');

    if (contentType && contentType.includes('application/json')) {
      return c.json();
    }

    return c.text() as Promise<T>;
  };

  // NOTE: Update just base url
  const getUrl = (contextUrl: string): string => {
    const baseUrl = VITE_API_URL || window.location.origin;
    const url = new URL(baseUrl + contextUrl, baseUrl);
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

  export const customFetch = async <T>(
    url: string,
    options: RequestInit,
  ): Promise<T> => {
    const requestUrl = getUrl(url);
    const requestHeaders = getHeaders(options.headers);

    const requestInit: RequestInit = {
      ...options,
      headers: requestHeaders,
    };

    const request = new Request(requestUrl, requestInit);
    const response = await fetch(request);
    const data = await getBody<T>(response);

    return { status: response.status, data, headers: response.headers } as T;
  };
