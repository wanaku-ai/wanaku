import { type Page } from "@playwright/test";

export type MockReply = { status?: number; json?: unknown; body?: string; contentType?: string };
type Revision = { id: number } & Record<string, unknown>;

/** A mutation reply. `thenActive` replaces the active-policy reply after the request. */
type MutationReply = MockReply & { thenActive?: MockReply };

/**
 * Serves mocked action-policy routes for the Admin UI. `active` answers the active-policy
 * endpoint. Each revision is listed in the revision history and answers its detail route with
 * `{ data: { revision, policy } }`, unless `details` has a reply for its id. `updates` answers
 * each `PUT` in order. `activations` answers each activation `POST` in order. The returned
 * request lists capture the bodies and the activation target IDs.
 */
export async function mockActionPolicies(
  page: Page,
  options: {
    active: MockReply;
    revisions: Revision[];
    policy: unknown;
    details?: Record<number, MockReply>;
    updates?: MutationReply[];
    activations?: MutationReply[];
  },
) {
  const { revisions, policy, details = {} } = options;
  const updates = [...(options.updates ?? [])];
  const updateRequests: unknown[] = [];
  let active = options.active;
  const activations = [...(options.activations ?? [])];
  const activationRequests: { id: number; body: unknown }[] = [];
  await page.route(/\/api\/v1\/action-policies\/revisions\/\d+\/activate$/, (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const id = Number(new URL(route.request().url()).pathname.split("/").at(-2));
    activationRequests.push({ id, body: route.request().postDataJSON() });
    const { thenActive, ...reply } = activations.shift() ?? { status: 500, json: { error: "unexpected activation" } };
    if (thenActive) active = thenActive;
    return route.fulfill(reply);
  });
  await page.route("**/api/v1/action-policies", (route) => {
    if (route.request().method() !== "PUT") return route.fulfill(active);
    updateRequests.push(route.request().postDataJSON());
    const { thenActive, ...reply } = updates.shift() ?? { status: 500, json: { error: "unexpected update" } };
    if (thenActive) active = thenActive;
    return route.fulfill(reply);
  });
  await page.route("**/api/v1/action-policies/revisions", (route) => route.fulfill({ json: { data: revisions } }));
  for (const revision of revisions) {
    await page.route(`**/api/v1/action-policies/revisions/${revision.id}`, (route) =>
      route.fulfill(details[revision.id] ?? { json: { data: { revision, policy } } }),
    );
  }
  return { updateRequests, activationRequests };
}
