import { type Page } from "@playwright/test";

export type MockReply = { status?: number; json?: unknown; body?: string; contentType?: string };
type Revision = { id: number } & Record<string, unknown>;

/**
 * Serves mocked action-policy routes for the Admin UI. `active` answers the active-policy
 * endpoint. Each revision is listed in the revision history and answers its detail route with
 * `{ data: { revision, policy } }`, unless `details` has a reply for its id.
 */
export async function mockActionPolicies(
  page: Page,
  options: { active: MockReply; revisions: Revision[]; policy: unknown; details?: Record<number, MockReply> },
) {
  const { active, revisions, policy, details = {} } = options;
  await page.route("**/api/v1/action-policies", (route) => route.fulfill(active));
  await page.route("**/api/v1/action-policies/revisions", (route) => route.fulfill({ json: { data: revisions } }));
  for (const revision of revisions) {
    await page.route(`**/api/v1/action-policies/revisions/${revision.id}`, (route) =>
      route.fulfill(details[revision.id] ?? { json: { data: { revision, policy } } }),
    );
  }
}
