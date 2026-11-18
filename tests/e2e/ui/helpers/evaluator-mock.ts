import { type Page } from "@playwright/test";

export type MockReply = { status?: number; json?: unknown };

type Evaluators = Array<Record<string, unknown>>;
type Metadata = { id: number } & Record<string, unknown>;
export type RevisionEntry = { revision: Metadata; evaluators: Evaluators };

/** The mutable server state of the mock. A reply can change it with `apply`. */
export type EvaluatorMockState = { revisions: RevisionEntry[]; activeId?: number };

/** A reply to one write request. `apply` runs after the request and simulates a server-side change. */
export type WriteReply = MockReply & { apply?: (state: EvaluatorMockState) => void };

/**
 * Serves mocked evaluator routes for the Admin UI. The revisions answer the list, active and
 * detail routes. `updates` answers each `PUT /api/v1/evaluators` in order, and `activations`
 * answers each `POST /api/v1/evaluators/revisions/{id}/activate` in order. The returned
 * `updateRequests` and `activateRequests` hold the parsed bodies of the write requests.
 */
export async function mockEvaluators(
  page: Page,
  options: {
    revisions: RevisionEntry[];
    activeId?: number;
    updates?: WriteReply[];
    activations?: WriteReply[];
    connections?: string[];
  },
) {
  const state: EvaluatorMockState = { revisions: [...options.revisions], activeId: options.activeId };
  const updates = [...(options.updates ?? [])];
  const activations = [...(options.activations ?? [])];
  const updateRequests: unknown[] = [];
  const activateRequests: unknown[] = [];
  const unexpected: WriteReply = { status: 500, json: { error: "unexpected request" } };

  const write = (reply: WriteReply) => {
    const { apply, ...response } = reply;
    apply?.(state);
    return response;
  };
  const find = (id: number) => state.revisions.find((entry) => entry.revision.id === id);

  await page.route(/\/api\/v1\/evaluators(\/.*)?$/, (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^.*\/api\/v1\/evaluators/, "");
    const method = request.method();

    if (path === "" && method === "PUT") {
      updateRequests.push(request.postDataJSON());
      return route.fulfill(write(updates.shift() ?? unexpected));
    }
    if (path === "/llm-connections") {
      return route.fulfill({ json: { data: options.connections ?? ["local-llama"] } });
    }
    if (path === "/revisions") {
      return route.fulfill({ json: { data: state.revisions.map((entry) => entry.revision) } });
    }
    if (path === "/revisions/active") {
      const active = state.activeId === undefined ? undefined : find(state.activeId);
      return active
        ? route.fulfill({ json: { data: active } })
        : route.fulfill({ status: 404, json: { error: "no active revision" } });
    }
    const activate = /^\/revisions\/(\d+)\/activate$/.exec(path);
    if (activate && method === "POST") {
      activateRequests.push(request.postDataJSON());
      return route.fulfill(write(activations.shift() ?? unexpected));
    }
    const detail = /^\/revisions\/(\d+)$/.exec(path);
    const entry = detail ? find(Number(detail[1])) : undefined;
    if (entry) return route.fulfill({ json: { data: entry } });
    return route.fulfill({ status: 404, json: { error: "not found" } });
  });

  return { state, updateRequests, activateRequests };
}
