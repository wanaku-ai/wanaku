use std::path::PathBuf;

use serde::{Deserialize, Serialize};

/// Top-level evaluator configuration containing multiple evaluator definitions.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct EvaluatorsConfig {
    #[serde(default)]
    pub evaluators: Vec<EvaluatorDef>,
}

/// A single evaluator definition: trigger + LLM operation + processor.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
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
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
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
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
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
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "lowercase")]
pub enum LlmOperation {
    Classify,
    Filter,
    Augment,
}

/// Reference to a WASM processor module.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct ProcessorRef {
    #[cfg_attr(feature = "openapi", schema(value_type = String))]
    pub path: PathBuf,
}

/// What to do when a WASM action fails.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
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

#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_types::registry::DEFAULT_NAMESPACE;
    use wanaku_types::{TOOLS_CALL, TOOLS_LIST};

    fn trigger(method: &str, namespace: Option<&str>) -> TriggerDef {
        TriggerDef {
            method: method.to_owned(),
            namespace: namespace.map(str::to_owned),
        }
    }

    #[test]
    fn matches_requires_exact_method() {
        let t = trigger(TOOLS_CALL, None);
        assert!(t.matches(TOOLS_CALL, DEFAULT_NAMESPACE));
        assert!(!t.matches(TOOLS_LIST, DEFAULT_NAMESPACE));
    }

    #[test]
    fn matches_with_no_namespace_is_a_wildcard() {
        let t = trigger(TOOLS_CALL, None);
        assert!(t.matches(TOOLS_CALL, DEFAULT_NAMESPACE));
        assert!(t.matches(TOOLS_CALL, "other"));
        assert!(t.matches(TOOLS_CALL, ""));
    }

    #[test]
    fn matches_requires_exact_namespace_when_set() {
        let t = trigger(TOOLS_CALL, Some("prod"));
        assert!(t.matches(TOOLS_CALL, "prod"));
        assert!(!t.matches(TOOLS_CALL, "dev"));
    }

    #[test]
    fn matches_fails_when_both_method_and_namespace_differ() {
        let t = trigger(TOOLS_CALL, Some("prod"));
        assert!(!t.matches(TOOLS_LIST, "dev"));
    }

    #[test]
    fn error_policy_defaults_to_continue() {
        let json = serde_json::json!({
            "name": "eval-1",
            "trigger": {"method": TOOLS_CALL},
            "llm": {"operation": "classify", "prompt": "p", "connection": "c"},
            "processor": {"path": "/proc.wasm"}
        });
        let def: EvaluatorDef = serde_json::from_value(json).expect("valid config");
        assert!(matches!(def.on_error, ErrorPolicy::Continue));
        assert!(def.trigger.namespace.is_none());
    }

    #[test]
    fn llm_def_rejects_unknown_inline_connection_fields() {
        // `deny_unknown_fields` must turn legacy inline model/url/api_key into
        // an error rather than silently dropping them.
        let json = r#"{
            "operation": "classify",
            "prompt": "p",
            "connection": "c",
            "model": "llama3.2",
            "url": "http://localhost",
            "api_key": "secret"
        }"#;
        let result: Result<LlmDef, _> = serde_json::from_str(json);
        assert!(result.is_err(), "unknown fields must be rejected");
    }

    #[test]
    fn llm_operation_deserializes_lowercase() {
        let classify: LlmOperation = serde_json::from_str("\"classify\"").unwrap();
        assert!(matches!(classify, LlmOperation::Classify));
        let filter: LlmOperation = serde_json::from_str("\"filter\"").unwrap();
        assert!(matches!(filter, LlmOperation::Filter));
        let augment: LlmOperation = serde_json::from_str("\"augment\"").unwrap();
        assert!(matches!(augment, LlmOperation::Augment));
    }

    #[test]
    fn error_policy_deserializes_lowercase() {
        let block: ErrorPolicy = serde_json::from_str("\"block\"").unwrap();
        assert!(matches!(block, ErrorPolicy::Block));
        let cont: ErrorPolicy = serde_json::from_str("\"continue\"").unwrap();
        assert!(matches!(cont, ErrorPolicy::Continue));
    }
}
