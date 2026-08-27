use std::collections::HashMap;

use crate::interactions::Interaction;
use crate::registry::ToolEntry;

/// Bundles the MCP request state needed by the evaluator pipeline.
///
/// Assembled once per evaluator invocation from filter metadata and
/// registry lookups, then threaded through LLM operations, schema
/// validation, and retry logic.
#[derive(Debug)]
pub struct McpContext<'a> {
    /// JSON-RPC method, e.g. `"tools/call"` or `"tools/list"`.
    pub method: &'a str,
    /// Tool name extracted from `params.name` (set by the MCP filter).
    pub tool_name: Option<&'a str>,
    /// Tool-call arguments from `params.arguments`.
    pub arguments: &'a HashMap<String, String>,
    /// Tools visible in the current namespace (populated for `LlmOperation::Filter`).
    pub tools: &'a [ToolEntry],
    /// Recent conversation history for the active conversation ID.
    pub history: &'a [Interaction],
}

impl<'a> McpContext<'a> {
    /// Creates a new MCP context from the parsed request state.
    #[must_use]
    pub const fn new(
        method: &'a str,
        tool_name: Option<&'a str>,
        arguments: &'a HashMap<String, String>,
        tools: &'a [ToolEntry],
        history: &'a [Interaction],
    ) -> Self {
        Self { method, tool_name, arguments, tools, history }
    }
}
