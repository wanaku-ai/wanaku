//! Centralized MCP JSON-RPC method names.
//!
//! MCP method names (`tools/call`, `tools/list`, ...) are compared as raw
//! string literals in every filter and in the evaluator feature. Defining them
//! once here means a typo becomes a compile error instead of a silently-skipped
//! filter (the guard clauses just return `Continue` on a mismatch).
//!
//! This mirrors the pattern already established for metadata *keys* in
//! [`crate::metadata`]; these constants centralize the JSON-RPC method *values*.

/// `tools/call` — invoke a tool.
pub const TOOLS_CALL: &str = "tools/call";

/// `tools/list` — list the available tools.
pub const TOOLS_LIST: &str = "tools/list";

/// `resources/read` — read the contents of a resource.
pub const RESOURCES_READ: &str = "resources/read";

/// `resources/list` — list the available resources.
pub const RESOURCES_LIST: &str = "resources/list";

/// `resources/templates/list` — list the available resource templates.
pub const RESOURCES_TEMPLATES_LIST: &str = "resources/templates/list";

/// `prompts/list` — list the available prompts.
pub const PROMPTS_LIST: &str = "prompts/list";

/// `prompts/get` — retrieve a specific prompt.
pub const PROMPTS_GET: &str = "prompts/get";

/// `initialize` — begin the MCP session handshake.
pub const INITIALIZE: &str = "initialize";

/// `notifications/initialized` — client acknowledgement of initialization.
pub const NOTIFICATIONS_INITIALIZED: &str = "notifications/initialized";

/// `ping` — liveness check.
pub const PING: &str = "ping";
