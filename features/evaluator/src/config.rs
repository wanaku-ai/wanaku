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

/// What triggers this evaluator.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TriggerDef {
    pub method: String,
    #[serde(default)]
    pub namespace: Option<String>,
}

/// LLM operation configuration.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LlmDef {
    pub operation: LlmOperation,
    pub prompt: String,
    pub model: String,
    pub url: String,
    #[serde(default)]
    pub api_key: String,
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

fn default_on_error() -> ErrorPolicy {
    ErrorPolicy::Continue
}

impl TriggerDef {
    pub fn matches(&self, method: &str, namespace: &str) -> bool {
        if self.method != method {
            return false;
        }
        if let Some(ref ns) = self.namespace {
            if ns != namespace {
                return false;
            }
        }
        true
    }
}
