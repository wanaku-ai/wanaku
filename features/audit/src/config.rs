use serde::Deserialize;

use wanaku_types::audit::DEFAULT_AUDIT_CAPACITY;

const DEFAULT_PAYLOAD_LIMIT: usize = 16_384;

#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct AuditConfig {
    pub max_records: usize,
    pub capture_payloads: bool,
    pub payload_max_bytes: usize,
    pub include_default_redaction_rules: bool,
    pub sensitive_fields: Vec<String>,
    pub sensitive_json_pointers: Vec<String>,
    pub credential_markers: Vec<String>,
    pub token_prefixes: Vec<String>,
}

impl Default for AuditConfig {
    fn default() -> Self {
        Self {
            max_records: DEFAULT_AUDIT_CAPACITY,
            capture_payloads: false,
            payload_max_bytes: DEFAULT_PAYLOAD_LIMIT,
            include_default_redaction_rules: true,
            sensitive_fields: Vec::new(),
            sensitive_json_pointers: Vec::new(),
            credential_markers: Vec::new(),
            token_prefixes: Vec::new(),
        }
    }
}

impl AuditConfig {
    pub fn apply_environment(&mut self) {
        if let Some(value) = parse_usize("WANAKU_AUDIT_MAX_RECORDS") {
            self.max_records = value;
        }
        if let Some(value) = parse_usize("WANAKU_AUDIT_PAYLOAD_MAX_BYTES") {
            self.payload_max_bytes = value;
        }
        if let Ok(value) = std::env::var("WANAKU_AUDIT_CAPTURE_PAYLOADS") {
            self.capture_payloads =
                matches!(value.to_ascii_lowercase().as_str(), "1" | "true" | "yes");
        }
    }

    pub fn normalize(&mut self) {
        self.max_records = self.max_records.max(1);
        self.payload_max_bytes = self.payload_max_bytes.max(1);
    }
}

fn parse_usize(name: &str) -> Option<usize> {
    std::env::var(name).ok()?.parse().ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_are_minimal_and_bounded() {
        let config = AuditConfig::default();
        assert_eq!(config.max_records, 10_000);
        assert!(!config.capture_payloads);
        assert_eq!(config.payload_max_bytes, 16_384);
        assert!(config.include_default_redaction_rules);
        assert!(config.sensitive_fields.is_empty());
        assert!(config.credential_markers.is_empty());
        assert!(config.token_prefixes.is_empty());
    }

    #[test]
    fn yaml_config_accepts_redaction_controls() {
        let value = serde_yaml::from_str::<AuditConfig>(
            "max_records: 25\ncapture_payloads: true\nsensitive_fields:\n  - private\nsensitive_json_pointers:\n  - /params/arguments/secret\ncredential_markers:\n  - 'credential '\ntoken_prefixes:\n  - custom_\n",
        );
        assert!(value.is_ok());
        if let Ok(config) = value {
            assert_eq!(config.max_records, 25);
            assert!(config.capture_payloads);
            assert_eq!(config.sensitive_fields, ["private"]);
            assert_eq!(config.sensitive_json_pointers.len(), 1);
            assert_eq!(config.credential_markers, ["credential "]);
            assert_eq!(config.token_prefixes, ["custom_"]);
        }
    }

    #[test]
    fn legacy_yaml_adds_rules_to_secure_defaults() {
        let config = serde_yaml::from_str::<AuditConfig>(
            "max_records: 25\nsensitive_fields:\n  - private\ncredential_markers:\n  - 'credential '\ntoken_prefixes:\n  - custom_\n",
        );

        assert!(config.is_ok());
        if let Ok(config) = config {
            assert!(config.include_default_redaction_rules);
            assert_eq!(config.sensitive_fields, ["private"]);
            assert_eq!(config.credential_markers, ["credential "]);
            assert_eq!(config.token_prefixes, ["custom_"]);
        }
    }

    #[test]
    fn explicit_opt_out_disables_default_rules() {
        let config = serde_yaml::from_str::<AuditConfig>(
            "include_default_redaction_rules: false\nsensitive_fields: []\ncredential_markers: []\ntoken_prefixes: []\n",
        );

        assert!(config.is_ok());
        if let Ok(config) = config {
            assert!(!config.include_default_redaction_rules);
            assert!(config.sensitive_fields.is_empty());
            assert!(config.credential_markers.is_empty());
            assert!(config.token_prefixes.is_empty());
        }
    }
}
