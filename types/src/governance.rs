use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Controls whether governance decisions affect traffic.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "lowercase")]
pub enum EnforcementMode {
    #[default]
    Enforce,
    Audit,
    Disabled,
}

/// Controls the result when no governance rule matches an action.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "lowercase")]
pub enum NoMatchBehavior {
    Allow,
    #[default]
    Deny,
}

/// Controls the result when governance cannot produce a decision.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "lowercase")]
pub enum FailureBehavior {
    Allow,
    #[default]
    Deny,
}

/// Controls whether audit mode invokes configured LLM evaluators.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "lowercase")]
pub enum AuditLevel {
    /// Record traffic and deterministic policy decisions without invoking LLMs.
    #[default]
    Basic,
    /// Run the full evaluator pipeline, including configured LLM calls.
    Full,
}

/// The effective governance settings for one namespace.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(default, deny_unknown_fields)]
pub struct GovernancePosture {
    pub mode: EnforcementMode,
    pub no_match: NoMatchBehavior,
    pub on_failure: FailureBehavior,
    pub audit_level: AuditLevel,
    /// Required when governance is intentionally disabled.
    pub disabled_reason: Option<String>,
}

/// A namespace override. Omitted fields inherit the global posture.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(default, deny_unknown_fields)]
pub struct GovernancePostureOverride {
    pub mode: Option<EnforcementMode>,
    pub no_match: Option<NoMatchBehavior>,
    pub on_failure: Option<FailureBehavior>,
    pub audit_level: Option<AuditLevel>,
    pub disabled_reason: Option<String>,
}

/// Global governance posture and namespace-specific overrides.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(default, deny_unknown_fields)]
pub struct GovernanceConfig {
    pub default: GovernancePosture,
    pub namespaces: BTreeMap<String, GovernancePostureOverride>,
}

#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum GovernanceConfigError {
    #[error("disabled governance scope '{scope}' requires a reason")]
    DisabledReasonRequired { scope: String },
    #[error("governance namespace must not be empty")]
    EmptyNamespace,
}

impl GovernanceConfig {
    /// Resolve one immutable posture without depending on declaration order.
    #[must_use]
    pub fn resolve(&self, namespace: &str) -> GovernancePosture {
        let Some(override_posture) = self.namespaces.get(namespace) else {
            return self.default.clone();
        };
        GovernancePosture {
            mode: override_posture.mode.unwrap_or(self.default.mode),
            no_match: override_posture.no_match.unwrap_or(self.default.no_match),
            on_failure: override_posture
                .on_failure
                .unwrap_or(self.default.on_failure),
            audit_level: override_posture
                .audit_level
                .unwrap_or(self.default.audit_level),
            disabled_reason: override_posture
                .disabled_reason
                .clone()
                .or_else(|| self.default.disabled_reason.clone()),
        }
    }

    pub fn validate(&self) -> Result<(), GovernanceConfigError> {
        validate_posture("global", &self.default)?;
        for namespace in self.namespaces.keys() {
            if namespace.trim().is_empty() {
                return Err(GovernanceConfigError::EmptyNamespace);
            }
            validate_posture(namespace, &self.resolve(namespace))?;
        }
        Ok(())
    }
}

fn validate_posture(scope: &str, posture: &GovernancePosture) -> Result<(), GovernanceConfigError> {
    if posture.mode == EnforcementMode::Disabled
        && posture
            .disabled_reason
            .as_deref()
            .is_none_or(|reason| reason.trim().is_empty())
    {
        return Err(GovernanceConfigError::DisabledReasonRequired {
            scope: scope.to_owned(),
        });
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_are_fail_safe() {
        let posture = GovernanceConfig::default().resolve("default");

        assert_eq!(posture.mode, EnforcementMode::Enforce);
        assert_eq!(posture.no_match, NoMatchBehavior::Deny);
        assert_eq!(posture.on_failure, FailureBehavior::Deny);
        assert_eq!(posture.audit_level, AuditLevel::Basic);
    }

    #[test]
    fn namespace_override_inherits_unspecified_global_values() {
        let config: GovernanceConfig = serde_yaml::from_str(
            r#"
default:
  mode: enforce
  no_match: deny
  on_failure: deny
namespaces:
  sandbox:
    mode: audit
    no_match: allow
    audit_level: full
"#,
        )
        .unwrap();

        assert_eq!(
            config.resolve("sandbox"),
            GovernancePosture {
                mode: EnforcementMode::Audit,
                no_match: NoMatchBehavior::Allow,
                on_failure: FailureBehavior::Deny,
                audit_level: AuditLevel::Full,
                disabled_reason: None,
            }
        );
        assert_eq!(config.resolve("production"), config.default);
    }

    #[test]
    fn disabled_scope_requires_non_empty_reason() {
        let mut config = GovernanceConfig::default();
        config.namespaces.insert(
            "maintenance".to_owned(),
            GovernancePostureOverride {
                mode: Some(EnforcementMode::Disabled),
                ..GovernancePostureOverride::default()
            },
        );

        assert_eq!(
            config.validate(),
            Err(GovernanceConfigError::DisabledReasonRequired {
                scope: "maintenance".to_owned(),
            })
        );

        config
            .namespaces
            .get_mut("maintenance")
            .unwrap()
            .disabled_reason = Some("scheduled maintenance".to_owned());
        assert_eq!(config.validate(), Ok(()));
    }

    #[test]
    fn explicit_global_disabled_scope_requires_reason() {
        let config = GovernanceConfig {
            default: GovernancePosture {
                mode: EnforcementMode::Disabled,
                disabled_reason: Some(" ".to_owned()),
                ..GovernancePosture::default()
            },
            namespaces: BTreeMap::new(),
        };

        assert_eq!(
            config.validate(),
            Err(GovernanceConfigError::DisabledReasonRequired {
                scope: "global".to_owned(),
            })
        );
    }
}
