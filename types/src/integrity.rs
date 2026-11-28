//! Transport-neutral inspection hooks for governed actions.

use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ContentKind {
    ToolArguments,
    ToolResult,
    ResourceContents,
    PromptContents,
    UpstreamError,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum IntegrityAction {
    Allow,
    Tag,
    Redact,
    RequireApproval,
    Block,
}

#[derive(Debug, Clone, Serialize)]
pub struct Finding {
    pub rule: String,
    pub scanner: &'static str,
    pub check: &'static str,
    pub action: IntegrityAction,
}

#[derive(Debug, Serialize)]
pub struct InspectionFailure {
    pub scanner: &'static str,
    pub scanner_id: Option<String>,
    pub reason: &'static str,
}

#[derive(Debug, Default)]
pub struct Inspection {
    pub findings: Vec<Finding>,
    pub failure: Option<InspectionFailure>,
    pub replacement: Option<Value>,
}

/// Adapters supply normalized JSON content and apply the resulting policy.
#[async_trait::async_trait]
pub trait ContentInspector: Send + Sync {
    async fn inspect_input(&self, arguments: &Value) -> Inspection;

    async fn inspect_outcome(&self, kind: ContentKind, content: &Value) -> Inspection;
}
