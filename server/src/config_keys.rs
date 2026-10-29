//! Unknown-key check for `wanaku.yaml`.
//!
//! Serde ignores unknown keys unless a type sets `deny_unknown_fields`, and
//! each feature handles a parse error in its own way. A misspelled key could
//! therefore drop configuration without an error. [`reject_unknown_keys`]
//! applies one rule to the complete file before the features load it: an
//! unknown key stops startup.

use serde::de::DeserializeOwned;
use serde_yaml::Value;

use wanaku_feature_action_policy::ActionPolicy;
use wanaku_feature_audit::AuditConfig;
use wanaku_feature_evaluator::config::{
    EvaluatorDef, LlmConnection, OpaConnection, SystemOneConnection,
};
use wanaku_feature_plugins::PluginConfig;
use wanaku_feature_simulation::SimulationLimits;
use wanaku_types::credentials::CredentialBinding;
use wanaku_types::governance::GovernanceConfig;
use wanaku_types::registry::ForwardEntry;

/// Reject unknown keys at any level of the Wanaku configuration.
///
/// The check reports unknown keys only. The feature that owns a section still
/// validates its values.
///
/// # Errors
///
/// Returns an error that names each unknown key with its path.
pub fn reject_unknown_keys(root: &Value) -> Result<(), String> {
    let Some(mapping) = root.as_mapping() else {
        return Ok(());
    };
    let mut unknown = Vec::new();
    for (key, value) in mapping {
        match key
            .as_str()
            .and_then(|key| section_unknown_keys(key, value))
        {
            Some(paths) => {
                let key = key.as_str().unwrap_or_default();
                unknown.extend(paths.into_iter().map(|path| format!("{key}{path}")));
            }
            None => unknown.push(
                serde_yaml::to_string(key)
                    .unwrap_or_default()
                    .trim()
                    .to_owned(),
            ),
        }
    }
    if unknown.is_empty() {
        Ok(())
    } else {
        Err(format!(
            "unknown keys in wanaku configuration: {}",
            unknown.join(", ")
        ))
    }
}

/// Return the unknown keys in one top-level section, or `None` when the
/// server does not read the section.
fn section_unknown_keys(key: &str, value: &Value) -> Option<Vec<String>> {
    let paths = match key {
        "forwards" => unknown_keys::<Vec<ForwardEntry>>(value),
        "bindings" => unknown_keys::<Vec<CredentialBinding>>(value),
        "llm_connections" => unknown_keys::<Vec<LlmConnection>>(value),
        "typesafe_system_one_connections" => unknown_keys::<Vec<SystemOneConnection>>(value),
        "opa_connections" => unknown_keys::<Vec<OpaConnection>>(value),
        "evaluators" => unknown_keys::<Vec<EvaluatorDef>>(value),
        "action_policy" => unknown_keys::<ActionPolicy>(value),
        "simulation" => unknown_keys::<SimulationLimits>(value),
        "governance" => unknown_keys::<GovernanceConfig>(value),
        "audit" => unknown_keys::<AuditConfig>(value),
        "plugins" => unknown_keys::<Vec<PluginConfig>>(value),
        "evaluator_startup_failure" => Vec::new(),
        _ => return None,
    };
    Some(paths)
}

/// Return the unknown keys in one section.
///
/// Some types set `deny_unknown_fields`. For them, serde reports the first
/// unknown key as a parse error. The feature that owns the section handles
/// all other parse errors.
fn unknown_keys<T: DeserializeOwned>(value: &Value) -> Vec<String> {
    let mut unknown = Vec::new();
    let result: Result<T, _> = serde_ignored::deserialize(value.clone(), |path| {
        unknown.push(format!(".{path}"));
    });
    if let Err(error) = result {
        let error = error.to_string();
        if error.contains("unknown field") {
            unknown.push(format!(" ({error})"));
        }
    }
    unknown
}

#[cfg(test)]
mod tests {
    use super::reject_unknown_keys;

    fn check(yaml: &str) -> Result<(), String> {
        reject_unknown_keys(&serde_yaml::from_str(yaml).unwrap())
    }

    #[test]
    fn accepts_known_keys() {
        check(
            r#"
forwards:
  - name: my-forward
    address: "http://127.0.0.1:3000/mcp"
    credentialBindings:
      discovery: my-binding
bindings:
  - id: my-binding
    forwardId: my-forward
    origin: "http://127.0.0.1:3000"
    mechanism:
      type: bearer
    secretRefs:
      - "env:API_TOKEN"
    allowedPurposes:
      - discovery
    cache:
      maxTtlSeconds: 60
evaluator_startup_failure: abort
plugins:
  - id: my-plugin
    services:
      customer-api:
        target: "http://127.0.0.1:8080"
"#,
        )
        .unwrap();
    }

    #[test]
    fn accepts_snake_case_forward_and_binding_keys() {
        check(
            r#"
forwards:
  - name: my-forward
    address: "http://127.0.0.1:3000/mcp"
    credential_bindings:
      discovery: my-binding
bindings:
  - id: my-binding
    forward_id: my-forward
    origin: "http://127.0.0.1:3000"
    mechanism:
      type: bearer
    secret_refs:
      - "env:API_TOKEN"
    allowed_purposes:
      - discovery
    restrictions:
      governed_items:
        - search
    cache:
      max_ttl_seconds: 60
"#,
        )
        .unwrap();
    }

    #[test]
    fn rejects_unknown_top_level_key() {
        let error = check("evaluator: []").unwrap_err();
        assert!(error.contains("evaluator"), "{error}");
    }

    #[test]
    fn rejects_misspelled_forward_key() {
        let error = check(
            r#"
forwards:
  - name: my-forward
    address: "http://127.0.0.1:3000/mcp"
    credentialsBindings:
      discovery: my-binding
"#,
        )
        .unwrap_err();
        assert!(error.contains("forwards.0.credentialsBindings"), "{error}");
    }

    #[test]
    fn rejects_misspelled_trigger_namespace() {
        let error = check(
            r#"
evaluators:
  - name: gate
    trigger:
      method: tools/call
      namepsace: finance
    engine:
      type: passthrough
    processor:
      path: gate.wasm
"#,
        )
        .unwrap_err();
        assert!(error.contains("namepsace"), "{error}");
    }

    #[test]
    fn rejects_unknown_key_in_llm_connection() {
        let error = check(
            r#"
llm_connections:
  - name: local
    url: "http://127.0.0.1:11434/v1"
    model: llama
    apikey: secret
"#,
        )
        .unwrap_err();
        assert!(error.contains("llm_connections.0.apikey"), "{error}");
    }

    #[test]
    fn rejects_misspelled_redaction_key() {
        let error = check("audit:\n  sensitive_field: [password]\n").unwrap_err();
        assert!(error.contains("audit.sensitive_field"), "{error}");
    }

    #[test]
    fn rejects_unknown_key_in_deny_unknown_fields_section() {
        let error = check("governance:\n  defualt_mode: enforce\n").unwrap_err();
        assert!(error.contains("defualt_mode"), "{error}");
    }

    #[test]
    fn leaves_value_errors_to_the_owning_feature() {
        check("simulation:\n  max_actions: not-a-number\n").unwrap();
    }

    #[test]
    fn lists_every_unknown_key() {
        let error = check("evaluator: []\nplugins:\n  - id: p\n    permissions: []\n").unwrap_err();
        assert!(error.contains("evaluator"), "{error}");
        assert!(error.contains("plugins.0.permissions"), "{error}");
    }
}
