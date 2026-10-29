//! Checks that the committed `wanaku.yaml` files parse with the production
//! configuration types.
//!
//! The documentation points users to these files as a starting point. The
//! tests catch drift between them and the loaders, for example a removed key
//! that would stop startup. They parse and cross-check the sections only.
//! They do not compile WASM processors or contact upstreams.
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used, clippy::panic))]

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use serde::de::DeserializeOwned;
use serde_yaml::Value;

use wanaku_feature_action_policy::ActionPolicy;
use wanaku_feature_evaluator::config::{
    EvaluationEngine, EvaluatorDef, EvaluatorsConfig, LlmConnection, OpaConnection,
    SystemOneConnection,
};
use wanaku_types::credentials::{CredentialBinding, NormalizedOrigin};
use wanaku_types::governance::GovernanceConfig;
use wanaku_types::registry::ForwardEntry;

/// Top-level keys that a Wanaku feature reads. The server ignores other keys.
const KNOWN_TOP_LEVEL_KEYS: &[&str] = &[
    "forwards",
    "bindings",
    "llm_connections",
    "typesafe_system_one_connections",
    "opa_connections",
    "evaluators",
    "evaluator_startup_failure",
    "action_policy",
    "simulation",
    "governance",
    "audit",
    "plugins",
];

fn repo_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("workspace root")
        .to_path_buf()
}

/// The repository sample and every `examples/<name>/wanaku.yaml`.
fn committed_configs() -> Vec<PathBuf> {
    let root = repo_root();
    let mut configs = vec![root.join("wanaku.yaml")];
    for entry in std::fs::read_dir(root.join("examples")).expect("examples directory") {
        let candidate = entry.expect("examples entry").path().join("wanaku.yaml");
        if candidate.is_file() {
            configs.push(candidate);
        }
    }
    configs.sort();
    configs
}

/// Deserialize a section and fail on keys that serde would silently ignore.
fn strict<T: DeserializeOwned>(path: &Path, section: &str, value: &Value) -> T {
    let mut ignored = Vec::new();
    let parsed = serde_ignored::deserialize(value.clone(), |key| ignored.push(key.to_string()))
        .unwrap_or_else(|error| panic!("{}: invalid `{section}`: {error}", path.display()));
    assert!(
        ignored.is_empty(),
        "{}: unknown keys in `{section}`: {ignored:?}",
        path.display()
    );
    parsed
}

fn section<T: DeserializeOwned>(path: &Path, root: &Value, name: &str) -> Option<T> {
    root.get(name).map(|value| strict(path, name, value))
}

fn check_config(path: &Path) {
    let content = std::fs::read_to_string(path).expect("readable config");
    let root: Value = serde_yaml::from_str(&content).expect("valid YAML");

    for key in root.as_mapping().expect("top-level mapping").keys() {
        let key = key.as_str().expect("string key");
        assert!(
            KNOWN_TOP_LEVEL_KEYS.contains(&key),
            "{}: unknown top-level key `{key}`",
            path.display()
        );
    }

    if let Some(governance) = section::<GovernanceConfig>(path, &root, "governance") {
        governance
            .validate()
            .unwrap_or_else(|error| panic!("{}: governance: {error}", path.display()));
    }
    check_forwards_and_bindings(path, &root);
    check_evaluators(path, &root);
    if let Some(policy) = section::<ActionPolicy>(path, &root, "action_policy") {
        policy
            .compile()
            .unwrap_or_else(|error| panic!("{}: action_policy: {error}", path.display()));
    }
}

fn check_forwards_and_bindings(path: &Path, root: &Value) {
    let bindings: Vec<CredentialBinding> = section(path, root, "bindings").unwrap_or_default();
    for binding in &bindings {
        NormalizedOrigin::from_address(binding.origin.as_str())
            .unwrap_or_else(|error| panic!("{}: binding {}: {error}", path.display(), binding.id));
        binding
            .validate()
            .unwrap_or_else(|error| panic!("{}: binding {}: {error}", path.display(), binding.id));
    }

    let forwards: Vec<ForwardEntry> = section(path, root, "forwards").unwrap_or_default();
    for forward in &forwards {
        for binding_id in forward.credential_bindings.values() {
            let owner = bindings
                .iter()
                .find(|binding| &binding.id == binding_id)
                .map(|binding| binding.forward_id.as_str());
            assert_eq!(
                owner,
                Some(forward.name.as_str()),
                "{}: forward {} references binding {binding_id}, which is missing or owned by another forward",
                path.display(),
                forward.name
            );
        }
    }
}

fn check_evaluators(path: &Path, root: &Value) {
    let mut connections = HashSet::new();
    let llm: Vec<LlmConnection> = section(path, root, "llm_connections").unwrap_or_default();
    connections.extend(llm.into_iter().map(|connection| connection.name));
    let system_one: Vec<SystemOneConnection> =
        section(path, root, "typesafe_system_one_connections").unwrap_or_default();
    connections.extend(system_one.into_iter().map(|connection| connection.name));
    let opa: Vec<OpaConnection> = section(path, root, "opa_connections").unwrap_or_default();
    connections.extend(opa.into_iter().map(|connection| connection.name));

    let evaluators: Vec<EvaluatorDef> = section(path, root, "evaluators").unwrap_or_default();
    for evaluator in &evaluators {
        let connection = match &evaluator.engine {
            EvaluationEngine::Llm(def) => &def.connection,
            EvaluationEngine::TypesafeSystemOne(def) => &def.connection,
            EvaluationEngine::Opa(def) => &def.connection,
            EvaluationEngine::Passthrough => continue,
        };
        assert!(
            connections.contains(connection),
            "{}: evaluator {} references unknown connection {connection}",
            path.display(),
            evaluator.name
        );
    }
}

#[test]
fn committed_wanaku_configs_match_the_loaders() {
    let configs = committed_configs();
    assert!(
        configs.len() > 1,
        "expected the sample and at least one example"
    );
    for config in &configs {
        check_config(config);
    }
}

#[test]
fn rejects_the_removed_llm_evaluator_key() {
    let legacy: Value = serde_yaml::from_str(
        r#"
evaluators:
  - name: "safety-gate"
    trigger:
      method: "tools/call"
    llm:
      operation: classify
      prompt: "Classify"
      connection: "local-llama"
    processor:
      path: "actions/dist/safety_review_action.wasm"
"#,
    )
    .unwrap();
    let parsed = serde_yaml::from_value::<EvaluatorsConfig>(legacy);
    assert!(parsed.is_err(), "the llm key must not parse");
}
