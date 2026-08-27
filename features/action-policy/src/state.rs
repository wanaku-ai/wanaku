use std::sync::{Arc, RwLock};

use crate::{ActionPolicy, CompiledPolicy};

#[derive(Clone)]
pub enum PolicySnapshot {
    Unconfigured,
    Valid(Arc<CompiledPolicy>),
    Invalid,
}

#[derive(Clone)]
pub struct ActionPolicyState {
    snapshot: Arc<RwLock<PolicySnapshot>>,
}

impl ActionPolicyState {
    #[must_use]
    pub fn new() -> Self {
        Self {
            snapshot: Arc::new(RwLock::new(PolicySnapshot::Unconfigured)),
        }
    }

    #[must_use]
    pub fn snapshot(&self) -> PolicySnapshot {
        match self.snapshot.read() {
            Ok(snapshot) => snapshot.clone(),
            Err(_) => PolicySnapshot::Invalid,
        }
    }

    pub(crate) fn load_yaml(&self, value: Option<&serde_yaml::Value>) {
        let snapshot = match value {
            None => PolicySnapshot::Unconfigured,
            Some(value) => match serde_yaml::from_value::<ActionPolicy>(value.clone()) {
                Ok(policy) => match policy.compile() {
                    Ok(compiled) => PolicySnapshot::Valid(Arc::new(compiled)),
                    Err(error) => {
                        tracing::error!(error = %error, "action policy validation failed");
                        PolicySnapshot::Invalid
                    }
                },
                Err(error) => {
                    tracing::error!(error = %error, "action policy configuration is invalid");
                    PolicySnapshot::Invalid
                }
            },
        };
        match self.snapshot.write() {
            Ok(mut active) => *active = snapshot,
            Err(error) => {
                tracing::error!(error = %error, "action policy state lock is unavailable");
            }
        }
    }
}

impl Default for ActionPolicyState {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loads_valid_missing_and_invalid_yaml_states() {
        let state = ActionPolicyState::new();
        assert!(matches!(state.snapshot(), PolicySnapshot::Unconfigured));

        let valid: serde_yaml::Value = serde_yaml::from_str(
            r#"
rules:
  - id: deny-delete
    effect: deny
    selectors:
      operation: tools/call
"#,
        )
        .expect("valid YAML");
        state.load_yaml(Some(&valid));
        assert!(matches!(state.snapshot(), PolicySnapshot::Valid(_)));

        let invalid: serde_yaml::Value = serde_yaml::from_str(
            r#"
rules:
  - id: no-selectors
    effect: deny
"#,
        )
        .expect("valid YAML shape");
        state.load_yaml(Some(&invalid));
        assert!(matches!(state.snapshot(), PolicySnapshot::Invalid));

        state.load_yaml(None);
        assert!(matches!(state.snapshot(), PolicySnapshot::Unconfigured));
    }
}
