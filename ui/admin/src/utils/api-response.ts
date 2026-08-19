export function unwrapData<T>(response: { data: T }): T {
  return response.data;
}
