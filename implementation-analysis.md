# Correlating Chat Completions with MCP Tool Calls

## Problem

The inference proxy (`:8083`) and the MCP endpoint (`:8081`) are separate request flows.
When an LLM responds with `tool_calls`, the client issues a separate MCP `tools/call`
request. There is no shared context linking the conversation that triggered the tool
call to the MCP request that executes it.

Four approaches are analyzed below.

---

## Approach 1: Shared Correlation Header

The client/orchestrator passes an `X-Conversation-Id` header on both the chat
completion request (`:8083`) and the MCP `tools/call` request (`:8081`). The proxy
logs and stores it on both sides.

### Implementation

- Extend `Interaction` struct with `conversation_id: Option<String>`
- Intercept filter (`intercept.rs`): extract `X-Conversation-Id` from request headers
- Tool call filter (`tool_call.rs`): extract same header, attach to log/stored data
- Management API: add query parameter to filter interactions by conversation ID
- No new filters, no new dependencies

### Changes

| File | Change |
|---|---|
| `apis/src/interactions.rs` | Add `conversation_id` field to `Interaction` |
| `filters/src/intercept.rs` | Read header from `ctx.request.headers` |
| `filters/src/tool_call.rs` | Read header, log alongside tool call |

### Pros

- Simplest to implement (< 50 lines changed)
- Reliable explicit correlation, no guessing
- Works with any LLM backend, not provider-specific
- No proxy-side state beyond what already exists

### Cons

- Requires client/orchestrator changes (must send the header)
- Client must be aware of the correlation protocol
- Two endpoints still exist -- client must coordinate both
- Useless if the client doesn't cooperate

---

## Approach 2: Proxy-Side Inference

The proxy parses the inference backend's chat completion response, detects `tool_calls` in the
output, generates a correlation ID, and stores a pending-tool-call mapping. When
an MCP `tools/call` arrives for that tool name within a time window, the proxy
auto-correlates.

### Implementation

- New `PendingToolCall` registry in `apis/`: a time-windowed map of
  `(tool_name, timestamp) -> correlation_id` with a configurable TTL (e.g. 30s)
- Intercept filter: after buffering the inference backend response, parse JSON for
  `choices[0].message.tool_calls`, extract tool names, generate UUID, store in
  pending registry
- Tool call filter: on `tools/call`, check pending registry for matching tool name
  within TTL window. If found, attach correlation ID to logs/interaction
- Pending entries expire via a background sweep or lazy eviction on read

### Changes

| File | Change |
|---|---|
| `apis/src/pending_tools.rs` | New: time-windowed correlation map |
| `filters/src/intercept.rs` | Parse response for `tool_calls`, register pending |
| `filters/src/tool_call.rs` | Look up pending correlation on `tools/call` |
| `server/src/pipelines.rs` | Inject `PendingToolRegistry` as extension |

### Pros

- No client changes required -- fully transparent
- Works with existing orchestrators that don't know about the proxy

### Cons

- **Ambiguous under concurrency**: if two clients call the same tool near-simultaneously,
  correlation is a coin flip
- Timing-dependent: if the MCP call is delayed past the TTL, correlation is lost
- Adds complexity and state (TTL map, background cleanup)
- False positives possible (unrelated tool call matches by name)
- Cannot correlate tool calls where the tool name differs between the LLM response
  and the MCP request (e.g. aliasing)

---

## Approach 3: Prompt Enrichment with Conversation ID

The proxy injects a system message containing a unique conversation ID into every
chat completion request. The LLM is instructed to include this ID in tool call
arguments. When the MCP `tools/call` arrives, the proxy extracts the ID from the
arguments.

### Implementation

- The existing `prompt_enrich` filter in praxis-ai is **static only** (no templates,
  no variable substitution). A custom dynamic enrichment filter is needed.
- New `ConversationTagFilter` in `filters/`:
  - `on_request_body`: generate UUID, parse JSON body, inject a system message like
    `"For all tool calls, include conversation_id: {uuid} in your arguments"`
  - Store the UUID in metadata (`ctx.set_metadata("conversation.id", uuid)`)
  - Re-serialize the modified body
- Intercept filter: read metadata to tag stored interactions with the conversation ID
- Tool call filter: extract `conversation_id` from tool call arguments
- Requires `request_body_access` = `ReadWrite` (not `ReadOnly`) since the body is modified

### Changes

| File | Change |
|---|---|
| `filters/src/conversation_tag.rs` | New: dynamic prompt injection filter |
| `filters/src/intercept.rs` | Read conversation ID from metadata |
| `filters/src/tool_call.rs` | Extract conversation ID from arguments |
| `server/src/default.yaml` | Add `conversation_tag` to inference_proxy chain |
| `server/src/lib.rs` | Register the new filter |

### Pros

- No client changes required
- Correlation ID travels through the LLM's own output path
- Works with any orchestrator

### Cons

- **Unreliable**: depends entirely on the LLM following the instruction. Smaller
  models or models under heavy load may ignore or mangle the ID
- Pollutes the system prompt with meta-instructions, wasting tokens
- Adds latency (body rewrite, extra tokens in prompt and response)
- Requires `ReadWrite` body access (more invasive filter)
- Cannot work with models that don't support function calling or don't reliably
  follow system prompts
- The injected conversation_id becomes part of the tool arguments, which the
  downstream tool must then ignore or strip

---

## Approach 4: Proxy-Orchestrated Agentic Loop

The proxy handles the full LLM -> tool call -> result -> LLM loop internally.
The client only talks to `:8083`. The proxy enriches prompts with available MCP
tools, detects tool calls in the inference backend's response, executes them via MCP, feeds
results back, and returns the final answer.

### How praxis-ai Does It

The praxis-ai `agentic_loop` filter does NOT loop itself. It is a loop controller
inside Praxis's **Iterative Request Router (IRR)**:

1. `agentic_loop` filter inspects the model response for tool calls
2. It writes `filter_results["agentic_loop"]["action"] = "loop"` or `"done"`
3. The IRR reads that signal and either re-enters the inference step or exits
4. Sibling filters handle tool execution: `openai_mcp_dispatch` for MCP tools,
   `openai_web_search` for web search
5. `ResponsesState` carries the conversation (messages, tool calls, usage) across
   iterations
6. Streaming is explicitly rejected (400 error) -- responses are fully buffered

This is a multi-filter orchestration, not a single filter.

### Implementation in wanaku-praxis

Two sub-options:

**4a. Leverage praxis-ai filters directly**

Register the relevant praxis-ai filters (`agentic_loop`, `openai_mcp_dispatch`,
`openai_responses_format`, `openai_responses_proxy`, etc.) and configure an IRR
pipeline. This requires:

- Enabling the Praxis IRR in `default.yaml` (`iterative_request_router` config)
- Registering ~8-10 praxis-ai filters via `register_ai_filters()`
- A tool enrichment filter that reads MCP tools from the registry and injects
  them as OpenAI-format tool definitions into the request body
- Configuring the inference backend cluster

| File | Change |
|---|---|
| `server/src/lib.rs` | Call `register_ai_filters()` for the needed subset |
| `filters/src/tool_enrich.rs` | New: reads tool registry, injects tool defs |
| `server/src/default.yaml` | IRR pipeline with inference + tool dispatch steps |
| `Cargo.toml` | May need additional praxis-ai features |

**4b. Self-contained loop filter**

Write a single custom filter that handles the entire loop:

1. `on_request_body`: parse client request, enrich with MCP tool definitions
2. Forward to the inference backend (let the request pass through to the upstream)
3. `on_response_body`: parse response, check for `tool_calls`
4. If tool calls present: call MCP tools directly (like `tool_call.rs` already does),
   build a new request with tool results, re-send to the inference backend via an HTTP client
5. Repeat until no tool calls or max iterations reached
6. Return final response to client

This is simpler but duplicates logic that praxis-ai already handles, and the
re-request from within `on_response_body` is awkward because that handler is
**synchronous** (not async) in Praxis.

Workaround: use `on_response` (async) instead, but response body is not available
there. Alternative: spawn a task and use `FilterAction::Reject` to return the final
assembled response, but this blocks the client until the full loop completes.

### Pros

- **Perfect correlation**: everything happens in one request context, no separate flows
- Client talks to a single endpoint, doesn't need to know about MCP
- Full observability at the proxy level
- Can add guardrails, rate limiting, caching at the proxy
- Leverages proven praxis-ai patterns (4a)

### Cons

- **Most complex to implement**: 4a requires ~8 filters + IRR config, 4b requires
  solving the sync `on_response_body` problem
- Streaming not supported (agentic_loop rejects `stream: true`)
- Proxy holds connections open for the entire multi-turn loop (latency, memory)
- Error handling is complex: tool failure mid-loop, timeout, partial results
- **`on_response_body` is synchronous** in Praxis, making 4b's in-filter loop
  architecturally difficult
- Tight coupling between proxy and both LLM + MCP backends
- Requires the inference backend to support OpenAI-compatible function calling format
  (most do, but not all models expose tools)

---

## Approach 5: Schema-Injected Conversation ID

When a tool is registered in the proxy, the registry automatically injects an
`X-Conversation-Id` required argument into the tool's `input_schema`. The LLM
sees it as a mandatory parameter during function calling and must supply a value.
The tool call filter extracts it for correlation and strips it before forwarding
to the actual tool backend.

### Implementation

- Tool registration (management API `POST /api/v1/tools` and forward discovery):
  after accepting a tool, mutate its `input_schema` to add `X-Conversation-Id`
  as a required string property
- Tool list filter (`tool_list.rs`): the injected argument is already in the schema,
  so the LLM sees it when it receives the tool definitions via `tools/list`
- Tool call filter (`tool_call.rs`): on `tools/call`, extract `X-Conversation-Id`
  from the arguments, log/store it for correlation, then strip it from the arguments
  before forwarding to the gRPC backend or MCP server
- Intercept filter (`intercept.rs`): on the inference proxy side, parse the response for
  `tool_calls` and extract the `X-Conversation-Id` the LLM filled in. Store it
  alongside the interaction for cross-endpoint correlation
- The orchestrator must set the conversation ID as a regular tool argument when
  calling `tools/call`. Alternatively, the proxy can inject a default via prompt
  enrichment (hybrid with approach 3), but this adds the unreliability of LLM
  instruction-following for the value itself

### Changes

| File | Change |
|---|---|
| `apis/src/registry.rs` | Mutate `input_schema` on tool registration to inject the argument |
| `filters/src/tool_call.rs` | Extract `X-Conversation-Id` from arguments, strip before forwarding |
| `filters/src/intercept.rs` | Parse inference backend response `tool_calls` for the conversation ID |
| `apis/src/interactions.rs` | Add `conversation_id` field to `Interaction` |

### Pros

- Leverages structured function calling -- required schema fields are enforced by
  the model's tool-calling protocol, much more reliable than free-text instructions
- No new filters needed, changes are in existing code paths
- Works transparently with any orchestrator that follows OpenAI tool calling format
- The correlation ID is a first-class tool argument, not a side-channel
- The LLM cannot skip it (required field) unlike approach 3's system prompt instruction

### Cons

- **The value still needs to come from somewhere**: the LLM must fill the field,
  but it doesn't inherently know the conversation ID. Two sub-cases:
  - If the orchestrator fills it: works perfectly, but requires orchestrator awareness
    (similar to approach 1, just via arguments instead of headers)
  - If the LLM generates it: the model will hallucinate a value (a random string or
    UUID-like), which is unique per call but not correlated across the chat completion
    and tool call unless the proxy recognizes it from the inference backend response
- Modifying tool schemas at registration time is a side effect the tool author didn't
  ask for -- could confuse tooling that validates schemas against a known contract
- The stripped argument must be removed cleanly before forwarding; if the downstream
  tool validates strictly, a leftover field could cause errors
- Only works with models that support structured function/tool calling (most modern
  models do, but not all served models)
- Forward-discovered tools (from remote MCP servers) get their schemas mutated, which
  may diverge from the upstream server's expectations

### Extracting Correlation from the LLM Response

When the LLM responds to a chat completion with `tool_calls`, each call includes
the arguments it generated. The intercept filter can parse:

```json
{
  "choices": [{
    "message": {
      "tool_calls": [{
        "function": {
          "name": "echo-tool",
          "arguments": "{\"wanaku_body\":\"hello\",\"X-Conversation-Id\":\"abc-123\"}"
        }
      }]
    }
  }]
}
```

The proxy extracts `abc-123` from the inference backend response and from the subsequent MCP
`tools/call` arguments, correlating both sides.

### Hybrid Variant: Proxy-Generated ID

To avoid depending on the orchestrator or LLM for the value:

1. Intercept filter generates a UUID when it sees a chat completion request
2. Injects it into the system prompt: `"Use 'abc-123' as the X-Conversation-Id for all tool calls"`
3. The LLM fills the required `X-Conversation-Id` field with the provided value
4. The tool call filter matches it

This combines approaches 3 and 5. The schema enforcement (approach 5) makes the
LLM more likely to include the field, while the prompt injection (approach 3)
provides the actual value. More reliable than approach 3 alone, but still depends
on the LLM copying the value correctly.

---

## Approach 6: Completion ID as Correlation Key

The inference backend response body already contains a unique `id` field (e.g. `"chatcmpl-327"`).
The intercept filter now extracts and exposes it via the interactions API. The
orchestrator receives this ID in the chat completion response alongside any
`tool_calls`, and passes it on the subsequent MCP `tools/call` request — either
as the standard `x-request-id` header (from the OpenAI protocol) or as a tool
argument.

This is not a new mechanism — it reuses an identifier the inference backend already generates.

### Implementation

The intercept filter already extracts `completion_id` from the response body
(implemented and verified). Remaining work:

- Tool call filter (`tool_call.rs`): read `x-request-id` header from MCP requests,
  log it alongside the tool call
- Optionally store it in the interaction store for MCP-side interactions too
  (would require the intercept filter on the MCP pipeline, or the tool call filter
  recording its own interactions)

### Changes

| File | Change |
|---|---|
| `filters/src/tool_call.rs` | Read `x-request-id` header, log for correlation |

That's it. The inference proxy side is already done. The `completion_id` is already in the
interactions API.

### Flow

1. Client sends chat completion to `:8083`
2. Inference backend responds with `{"id": "chatcmpl-327", "choices": [{"message": {"tool_calls": [...]}}]}`
3. Proxy records the interaction with `completion_id: "chatcmpl-327"` (already working)
4. Client receives the response, sees both `id` and `tool_calls`
5. Client sends MCP `tools/call` to `:8081` with header `x-request-id: chatcmpl-327`
6. Tool call filter reads the header and logs it
7. Correlation: query interactions by `completion_id`, match with MCP logs by
   `x-request-id`

### Pros

- **Minimal implementation**: one side is already done, the other is a one-line
  header read
- Uses identifiers that already exist — no generation, no injection, no schema mutation
- `x-request-id` is a standard OpenAI protocol header — client SDKs already support it
- No prompt pollution, no schema modification, no timing heuristics
- Works with any model served by the inference backend (not function-calling dependent)
- The `completion_id` is already visible in `GET /api/v1/interactions`

### Cons

- Requires the orchestrator to pass `x-request-id` on the MCP call (client awareness)
- If the orchestrator doesn't cooperate, no correlation (same as approach 1)
- The `chatcmpl-*` ID is per-completion, not per-conversation — multi-turn
  conversations produce multiple IDs. The orchestrator must decide which one to
  forward (typically the most recent one that contained `tool_calls`)
- The inference backend generates the ID, not the proxy — if the backend changes its ID format or
  stops including it, the approach breaks (unlikely for OpenAI-compat endpoints)

### Comparison with Approach 1

Approach 6 is a refinement of approach 1. The difference:

| | Approach 1 | Approach 6 |
|---|---|---|
| ID source | Client generates | Inference backend generates (already in response) |
| Header | Custom `X-Conversation-Id` | Standard `x-request-id` |
| Client work | Generate + send on both sides | Read from response + send on MCP side |
| Protocol fit | Custom convention | OpenAI standard |

Approach 6 is strictly better when the orchestrator speaks OpenAI protocol, because
the ID and the header are already part of the protocol. The client doesn't need to
learn a custom convention — it just forwards what it received.

---

## Summary

| Approach                    | Complexity | Reliability | Client Changes | Best For                         |
|-----------------------------|------------|-------------|----------------|----------------------------------|
| 1. Correlation header       | Low        | High        | Yes            | Controlled environments          |
| 2. Proxy inference          | Medium     | Low         | No             | Quick PoC, single-user           |
| 3. Prompt enrichment        | Medium     | Low         | No             | Experimental, strong models only |
| 4. Agentic loop             | High       | High        | No             | Production, single-endpoint UX   |
| 5. Schema-injected ID       | Low-Medium | Medium-High | Partial        | Structured tool calling models   |
| 6. Completion ID            | Very Low   | High        | Minimal        | OpenAI-compatible orchestrators  |

### Recommendation

For an immediate PoC: **Approach 6** (completion ID). Half the work is already
done — `completion_id` is already extracted and exposed in the interactions API.
The only remaining step is reading `x-request-id` in the tool call filter. It
uses existing protocol identifiers (the inference backend's `id` field + OpenAI's `x-request-id`
header), requires no schema mutation, no prompt injection, and no custom conventions.

For structured tool calling: **Approach 5** is a strong middle ground when the
orchestrator cannot be modified. It leverages the model's function calling protocol
to enforce the correlation field. If the orchestrator cooperates (fills the
conversation ID), reliability matches approach 6. If not, the hybrid variant
(schema + prompt injection) is the next best option.

For the end goal: **Approach 4a** (leveraging praxis-ai's agentic loop). It
eliminates the correlation problem entirely and gives the proxy full control. The
complexity is real but the patterns are proven in praxis-ai. The main prerequisite
is a tool enrichment filter that bridges the MCP registry to OpenAI tool definitions.

Approaches 2 and 3 occupy an awkward middle ground: they add complexity without
delivering reliable correlation. They may be useful as stepping stones but should
not be the target architecture.
