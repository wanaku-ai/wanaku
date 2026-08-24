use std::path::PathBuf;

use serde::{Deserialize, Serialize};

/// Top-level evaluator configuration containing multiple evaluator definitions.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EvaluatorsConfig {
    #[serde(default)]
    pub evaluators: Vec<EvaluatorDef>,
}

/// A single evaluator definition: trigger + LLM operation + processor.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EvaluatorDef {
    pub name: String,
    pub trigger: TriggerDef,
    pub llm: LlmDef,
    pub processor: ProcessorRef,
    #[serde(default = "default_on_error")]
    pub on_error: ErrorPolicy,
}

/// A named LLM connection: model, endpoint, and credential.
///
/// Connections are config-only. They are loaded from `llm_connections` in
/// `wanaku.yaml` at startup and are never part of the management API's
/// request or response shapes for evaluators — evaluators reference a
/// connection by name instead of embedding one, so credentials never
/// transit the management API.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LlmConnection {
    pub name: String,
    pub model: String,
    pub url: String,
    #[serde(default)]
    pub api_key: String,
}

/// What triggers this evaluator.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TriggerDef {
    pub method: String,
    #[serde(default)]
    pub namespace: Option<String>,
}

/// LLM operation configuration.
///
/// Carries only what an evaluator *does* with the LLM (operation, prompt,
/// result schema) plus a reference to a named [`LlmConnection`] configured
/// in `wanaku.yaml`. Connection details (model/url/api_key) are deliberately
/// not fields here: `deny_unknown_fields` turns any legacy inline
/// `model`/`url`/`api_key` in a client payload into a clear 400 instead of
/// silently dropping it.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LlmDef {
    pub operation: LlmOperation,
    pub prompt: String,
    pub connection: String,
    #[serde(default)]
    pub result_schema: Option<serde_json::Value>,
}

/// The type of cognitive operation the LLM performs.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LlmOperation {
    Classify,
    Filter,
    Augment,
}

/// Reference to a WASM processor module.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProcessorRef {
    pub path: PathBuf,
}

/// What to do when a WASM action fails.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ErrorPolicy {
    Continue,
    Block,
}

const fn default_on_error() -> ErrorPolicy {
    ErrorPolicy::Continue
}

impl TriggerDef {
    pub fn matches(&self, method: &str, namespace: &str) -> bool {
        if self.method != method {
            return false;
        }
        if let Some(ref ns) = self.namespace
            && ns != namespace {
                return false;
            }
        true
    }
}
