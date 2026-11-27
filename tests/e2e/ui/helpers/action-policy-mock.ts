import { type Page } from "@playwright/test";

export type MockReply = { status?: number; json?: unknown; body?: string; contentType?: string };
type Revision = { id: number } & Record<string, unknown>;
type RevisionReply = { json: { data: { revision: Revision; policy: unknown } } };

/** `thenActive` adds a new active revision and supersedes the former active revision. */
type MutationReply = MockReply & { thenActive?: RevisionReply };

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
    details?: Record<number, RevisionReply | (MockReply & { status: number })>;
    updates?: MutationReply[];
    activations?: MutationReply[];
  },
) {
  const { policy } = options;
  const revisions = new Map(options.revisions.map((revision) => [revision.id, revision]));
  const details = { ...options.details };
  const updates = [...(options.updates ?? [])];
  const updateRequests: unknown[] = [];
  let active = options.active;
  const activations = [...(options.activations ?? [])];
  const activationRequests: { id: number; body: unknown }[] = [];

  const applyMutation = ({ thenActive, ...reply }: MutationReply): MockReply => {
    if (thenActive) {
      const { revision } = thenActive.json.data;
      for (const [id, entry] of revisions) {
        if (entry.status === "active") revisions.set(id, { ...entry, status: "superseded" });
      }
      revisions.set(revision.id, revision);
      details[revision.id] = thenActive;
      active = thenActive;
    }
    return reply;
  };

  await page.route(/\/api\/v1\/action-policies\/revisions\/\d+\/activate$/, (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const id = Number(new URL(route.request().url()).pathname.split("/").at(-2));
    activationRequests.push({ id, body: route.request().postDataJSON() });
    return route.fulfill(applyMutation(activations.shift() ?? { status: 500, json: { error: "unexpected activation" } }));
  });
  await page.route("**/api/v1/action-policies", (route) => {
    if (route.request().method() !== "PUT") return route.fulfill(active);
    updateRequests.push(route.request().postDataJSON());
    return route.fulfill(applyMutation(updates.shift() ?? { status: 500, json: { error: "unexpected update" } }));
  });
  await page.route("**/api/v1/action-policies/revisions", (route) =>
    route.fulfill({ json: { data: [...revisions.values()].sort((a, b) => b.id - a.id) } }),
  );
  await page.route(/\/api\/v1\/action-policies\/revisions\/\d+$/, (route) => {
    const id = Number(new URL(route.request().url()).pathname.split("/").at(-1));
    const detail = details[id];
    if (detail && "status" in detail) return route.fulfill(detail);
    const revision = revisions.get(id);
    if (!revision) return route.fulfill({ status: 404, json: { error: "revision not found" } });
    return route.fulfill({ json: { data: { revision, policy: detail ? detail.json.data.policy : policy } } });
  });
  return { updateRequests, activationRequests };
}
