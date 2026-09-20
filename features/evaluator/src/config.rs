use std::path::PathBuf;

use serde::{Deserialize, Serialize};

pub use crate::engines::llm::{LlmConnection, LlmDef, LlmOperation};
pub use crate::engines::system_one::{
    NoulCriteria, NoulDef, SystemOneConnection, SystemOneDef, SystemOneState,
};

/// Top-level evaluator configuration containing multiple evaluator definitions.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct EvaluatorsConfig {
    #[serde(default)]
    pub evaluators: Vec<EvaluatorDef>,
}

/// A single evaluator definition: trigger + evaluation engine + processor.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct EvaluatorDef {
    pub name: String,
    pub trigger: TriggerDef,
    /// The engine that produces the processor input.
    pub engine: EvaluationEngine,
    pub processor: ProcessorRef,
    #[serde(default = "default_on_error")]
    pub on_error: ErrorPolicy,
}

/// An evaluation implementation selected by an evaluator definition.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(tag = "type", rename_all = "lowercase", deny_unknown_fields)]
pub enum EvaluationEngine {
    /// LLM-assisted classification, filtering, or augmentation.
    Llm(LlmDef),
    /// TypeSafe System One typed evaluation.
    #[serde(rename = "typesafe-system-one")]
    TypesafeSystemOne(SystemOneDef),
    /// Pass the normalized MCP context directly to the processor.
    Passthrough,
}

impl EvaluationEngine {
    /// Stable engine identifier for logs and metrics.
    #[must_use]
    pub const fn kind(&self) -> &'static str {
        match self {
            Self::Llm(_) => "llm",
            Self::TypesafeSystemOne(_) => "typesafe-system-one",
            Self::Passthrough => "passthrough",
        }
    }
}

/// What triggers this evaluator.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct TriggerDef {
    pub method: String,
    #[serde(default)]
    pub namespace: Option<String>,
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
            && ns != namespace
        {
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
            "engine": {"type": "llm", "operation": "classify", "prompt": "p", "connection": "c"},
            "processor": {"path": "/proc.wasm"}
        });
        let def: EvaluatorDef = serde_json::from_value(json).expect("valid config");
        assert!(matches!(def.on_error, ErrorPolicy::Continue));
        assert!(def.trigger.namespace.is_none());
    }

    #[test]
    fn legacy_llm_configuration_is_rejected() {
        let json = serde_json::json!({
            "name": "eval-1", "trigger": {"method": TOOLS_CALL},
            "llm": {"operation": "classify", "prompt": "p", "connection": "c"},
            "processor": {"path": "/proc.wasm"}
        });
        let result: Result<EvaluatorDef, _> = serde_json::from_value(json);
        assert!(result.is_err(), "legacy llm configuration must be rejected");
    }

    #[test]
    fn passthrough_engine_deserializes() {
        let engine: EvaluationEngine =
            serde_json::from_str(r#"{"type":"passthrough"}"#).expect("valid passthrough engine");
        assert!(matches!(engine, EvaluationEngine::Passthrough));
        assert_eq!(engine.kind(), "passthrough");
    }

    #[test]
    fn typesafe_system_one_engine_deserializes() {
        let engine: EvaluationEngine = serde_json::from_value(serde_json::json!({
            "type": "typesafe-system-one",
            "connection": "typesafe",
            "state": "arguments",
            "noul": {
                "id": "is_safe",
                "instructions": "Are these arguments safe?",
                "criteria": { "true": "Safe", "false": "Unsafe" }
            }
        }))
        .expect("valid System One engine");
        assert_eq!(engine.kind(), "typesafe-system-one");
        assert!(matches!(engine, EvaluationEngine::TypesafeSystemOne(_)));
        if let EvaluationEngine::TypesafeSystemOne(definition) = engine {
            assert_eq!(definition.noul.id, "is_safe");
            assert!(matches!(definition.state, SystemOneState::Arguments));
        }
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
