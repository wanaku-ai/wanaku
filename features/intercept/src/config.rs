use serde::Deserialize;

use wanaku_types::audit_redaction::{AuditRedactionRules, AuditRedactor};

/// Default upper bound, in bytes, for a buffered request or response body.
pub const DEFAULT_MAX_BODY_BYTES: usize = 4_194_304;

/// Redaction and capture settings for the intercept filter.
///
/// The intercept filter captures LLM request and response bodies for intent
/// analysis. This configuration redacts sensitive data from the retained
/// bodies before they reach the interaction store. See issue #1964.
#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct InterceptConfig {
    /// Upper bound, in bytes, for a buffered request or response body.
    pub max_body_bytes: usize,
    /// Capture request and response bodies. Enabled by default because intent
    /// analysis needs the conversation content. When disabled, the interaction
    /// store keeps only the envelope (path, status, model, timing).
    pub capture_payloads: bool,
    /// Upper bound, in bytes, for a retained body after redaction. A body that
    /// exceeds this bound is replaced with a single redaction marker.
    pub payload_max_bytes: usize,
    /// Apply the built-in redaction rules in addition to the configured rules.
    pub include_default_redaction_rules: bool,
    /// Additional field names to redact.
    pub sensitive_fields: Vec<String>,
    /// JSON Pointer paths to redact.
    pub sensitive_json_pointers: Vec<String>,
    /// Additional substrings that mark a value as a credential.
    pub credential_markers: Vec<String>,
    /// Additional token prefixes that mark a value as a credential.
    pub token_prefixes: Vec<String>,
}

impl Default for InterceptConfig {
    fn default() -> Self {
        Self {
            max_body_bytes: DEFAULT_MAX_BODY_BYTES,
            capture_payloads: true,
            payload_max_bytes: DEFAULT_MAX_BODY_BYTES,
            include_default_redaction_rules: true,
            sensitive_fields: Vec::new(),
            sensitive_json_pointers: Vec::new(),
            credential_markers: Vec::new(),
            token_prefixes: Vec::new(),
        }
    }
}

impl InterceptConfig {
    /// Parse the configuration from the filter YAML node.
    ///
    /// Unknown keys (for example `filter`) are ignored. A malformed node falls
    /// back to defaults with a warning.
    #[must_use]
    pub fn parse(config: &serde_yaml::Value) -> Self {
        if config.is_null() {
            return Self::default();
        }
        match serde_yaml::from_value::<Self>(config.clone()) {
            Ok(parsed) => parsed,
            Err(error) => {
                tracing::warn!(error = %error, "invalid intercept configuration, using defaults");
                Self::default()
            }
        }
    }

    /// Apply `WANAKU_INTERCEPT_*` environment overrides.
    pub fn apply_environment(&mut self) {
        if let Some(value) = parse_usize("WANAKU_INTERCEPT_MAX_BODY_BYTES") {
            self.max_body_bytes = value;
        }
        if let Some(value) = parse_usize("WANAKU_INTERCEPT_PAYLOAD_MAX_BYTES") {
            self.payload_max_bytes = value;
        }
        if let Ok(value) = std::env::var("WANAKU_INTERCEPT_CAPTURE_PAYLOADS") {
            self.capture_payloads =
                matches!(value.to_ascii_lowercase().as_str(), "1" | "true" | "yes");
        }
    }

    /// Clamp numeric bounds to safe minimums.
    pub fn normalize(&mut self) {
        self.max_body_bytes = self.max_body_bytes.max(1);
        self.payload_max_bytes = self.payload_max_bytes.max(1);
    }

    /// Build the redactor that applies these rules to captured bodies.
    #[must_use]
    pub fn into_redactor(self) -> AuditRedactor {
        AuditRedactor::new(
            AuditRedactionRules {
                include_defaults: self.include_default_redaction_rules,
                sensitive_fields: self.sensitive_fields,
                credential_markers: self.credential_markers,
                token_prefixes: self.token_prefixes,
            },
            self.sensitive_json_pointers,
            self.payload_max_bytes,
        )
    }
}

fn parse_usize(name: &str) -> Option<usize> {
    let raw = std::env::var(name).ok()?;
    match raw.parse() {
        Ok(value) => Some(value),
        Err(_) => {
            tracing::warn!(
                variable = name,
                value = %raw,
                "invalid numeric value for environment variable, ignoring"
            );
            None
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_capture_payloads_and_include_default_rules() {
        let config = InterceptConfig::default();
        assert!(config.capture_payloads);
        assert!(config.include_default_redaction_rules);
        assert_eq!(config.max_body_bytes, DEFAULT_MAX_BODY_BYTES);
        assert_eq!(config.payload_max_bytes, DEFAULT_MAX_BODY_BYTES);
        assert!(config.sensitive_fields.is_empty());
        assert!(config.sensitive_json_pointers.is_empty());
        assert!(config.credential_markers.is_empty());
        assert!(config.token_prefixes.is_empty());
    }

    #[test]
    fn parse_null_node_returns_defaults() {
        let config = InterceptConfig::parse(&serde_yaml::Value::Null);
        assert!(config.capture_payloads);
        assert_eq!(config.max_body_bytes, DEFAULT_MAX_BODY_BYTES);
    }

    #[test]
    fn parse_ignores_unknown_filter_key() {
        let value = serde_yaml::from_str::<serde_yaml::Value>("filter: wanaku_intercept")
            .expect("valid yaml");
        let config = InterceptConfig::parse(&value);
        assert!(config.capture_payloads);
        assert_eq!(config.max_body_bytes, DEFAULT_MAX_BODY_BYTES);
    }

    #[test]
    fn parse_reads_redaction_controls() {
        let value = serde_yaml::from_str::<serde_yaml::Value>(
            "filter: wanaku_intercept\ncapture_payloads: false\npayload_max_bytes: 2048\nsensitive_fields:\n  - private\nsensitive_json_pointers:\n  - /messages/0/content\ncredential_markers:\n  - 'credential '\ntoken_prefixes:\n  - custom_\n",
        )
        .expect("valid yaml");
        let config = InterceptConfig::parse(&value);
        assert!(!config.capture_payloads);
        assert_eq!(config.payload_max_bytes, 2048);
        assert_eq!(config.sensitive_fields, ["private"]);
        assert_eq!(config.sensitive_json_pointers, ["/messages/0/content"]);
        assert_eq!(config.credential_markers, ["credential "]);
        assert_eq!(config.token_prefixes, ["custom_"]);
    }

    #[test]
    fn normalize_clamps_zero_bounds() {
        let mut config = InterceptConfig {
            max_body_bytes: 0,
            payload_max_bytes: 0,
            ..InterceptConfig::default()
        };
        config.normalize();
        assert_eq!(config.max_body_bytes, 1);
        assert_eq!(config.payload_max_bytes, 1);
    }
}
