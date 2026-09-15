use serde_json::Value;

use crate::audit::RedactionMetadata;

const REDACTED: &str = "[REDACTED]";
const DEFAULT_SENSITIVE_FIELDS: &[&str] = &[
    "authorization",
    "cookie",
    "api_key",
    "apikey",
    "client_secret",
    "password",
    "token",
];
const BEARER_MARKER: &str = "bearer ";
const BASIC_MARKER: &str = "basic ";
const CLIENT_SECRET_MARKER: &str = "client_secret=";
const AWS_ACCESS_KEY_PREFIX: &str = "AKIA";
const JWT_PREFIX: &str = "eyJ";
#[derive(Debug, Clone)]
pub struct AuditRedactionRules {
    pub include_defaults: bool,
    pub sensitive_fields: Vec<String>,
    pub credential_markers: Vec<String>,
    pub token_prefixes: Vec<String>,
}

impl Default for AuditRedactionRules {
    fn default() -> Self {
        Self {
            include_defaults: true,
            sensitive_fields: Vec::new(),
            credential_markers: Vec::new(),
            token_prefixes: Vec::new(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct AuditRedactor {
    rules: AuditRedactionRules,
    json_pointers: Vec<String>,
    payload_limit: usize,
}

impl AuditRedactor {
    #[must_use]
    pub fn new(
        mut rules: AuditRedactionRules,
        json_pointers: Vec<String>,
        payload_limit: usize,
    ) -> Self {
        rules.sensitive_fields = normalize(rules.sensitive_fields);
        rules.credential_markers = normalize(rules.credential_markers);
        rules.token_prefixes = normalize(rules.token_prefixes);
        Self {
            rules,
            json_pointers,
            payload_limit,
        }
    }

    pub fn redact(&self, value: &mut Value) -> RedactionMetadata {
        let mut metadata = RedactionMetadata {
            payload_captured: true,
            ..RedactionMetadata::default()
        };
        for pointer in &self.json_pointers {
            if let Some(selected) = value.pointer_mut(pointer) {
                *selected = Value::String(REDACTED.to_owned());
                metadata.redacted_fields.push(pointer.clone());
            }
        }
        let rules = RedactionRules {
            include_defaults: self.rules.include_defaults,
            fields: &self.rules.sensitive_fields,
            credential_markers: &self.rules.credential_markers,
            token_prefixes: &self.rules.token_prefixes,
        };
        redact_value(value, "", rules, &mut metadata.redacted_fields);
        let encoded_len = serde_json::to_vec(value).map_or(0, |bytes| bytes.len());
        if encoded_len > self.payload_limit {
            *value = Value::String(REDACTED.to_owned());
            metadata.payload_truncated = true;
        }
        metadata
    }

    /// Redact a string field before an audit event is retained or persisted.
    pub fn redact_string(&self, value: &mut String) -> RedactionMetadata {
        let mut candidate = Value::String(std::mem::take(value));
        let metadata = self.redact(&mut candidate);
        *value = match candidate {
            Value::String(redacted) => redacted,
            _ => REDACTED.to_owned(),
        };
        metadata
    }
}

impl Default for AuditRedactor {
    fn default() -> Self {
        Self::new(AuditRedactionRules::default(), Vec::new(), 16_384)
    }
}

fn normalize(values: Vec<String>) -> Vec<String> {
    values
        .into_iter()
        .filter(|value| !value.trim().is_empty())
        .map(|value| value.to_ascii_lowercase())
        .collect()
}

#[derive(Clone, Copy)]
struct RedactionRules<'a> {
    include_defaults: bool,
    fields: &'a [String],
    credential_markers: &'a [String],
    token_prefixes: &'a [String],
}

fn redact_value(
    value: &mut Value,
    path: &str,
    rules: RedactionRules<'_>,
    redacted: &mut Vec<String>,
) {
    match value {
        Value::Object(object) => redact_object(object, path, rules, redacted),
        Value::Array(array) => {
            for (index, child) in array.iter_mut().enumerate() {
                redact_value(child, &format!("{path}/{index}"), rules, redacted);
            }
        }
        Value::String(text) if credential_shaped(text, rules) => {
            *text = REDACTED.to_owned();
            redacted.push(path.to_owned());
        }
        _ => {}
    }
}

fn redact_object(
    object: &mut serde_json::Map<String, Value>,
    path: &str,
    rules: RedactionRules<'_>,
    redacted: &mut Vec<String>,
) {
    for (key, child) in object {
        let child_path = format!("{path}/{key}");
        if sensitive_field(key, rules) {
            *child = Value::String(REDACTED.to_owned());
            redacted.push(child_path);
        } else {
            redact_value(child, &child_path, rules, redacted);
        }
    }
}

fn sensitive_field(key: &str, rules: RedactionRules<'_>) -> bool {
    rules
        .fields
        .iter()
        .any(|field| key.eq_ignore_ascii_case(field))
        || (rules.include_defaults
            && DEFAULT_SENSITIVE_FIELDS
                .iter()
                .any(|field| key.eq_ignore_ascii_case(field)))
}

fn credential_shaped(value: &str, rules: RedactionRules<'_>) -> bool {
    let lower = value.to_ascii_lowercase();
    (rules.include_defaults && default_credential_shaped(value, &lower))
        || rules
            .credential_markers
            .iter()
            .any(|marker| lower.contains(marker))
        || lower
            .split(token_separator)
            .any(|token| custom_credential_token(token, rules.token_prefixes))
}

fn default_credential_shaped(value: &str, lower: &str) -> bool {
    contains_auth_scheme(lower, BEARER_MARKER)
        || contains_auth_scheme(lower, BASIC_MARKER)
        || lower.contains(CLIENT_SECRET_MARKER)
        || value.split(token_separator).any(default_credential_token)
}

fn contains_auth_scheme(value: &str, scheme: &str) -> bool {
    value
        .match_indices(scheme)
        .any(|(index, _)| index == 0 || !value.as_bytes()[index - 1].is_ascii_alphanumeric())
}

const fn token_separator(character: char) -> bool {
    !character.is_ascii_alphanumeric() && !matches!(character, '_' | '-' | '.' | '=')
}

fn default_credential_token(value: &str) -> bool {
    let lower = value.to_ascii_lowercase();
    lower.starts_with("sk-")
        || lower.starts_with("ghp_")
        || lower.starts_with("github_pat_")
        || lower.starts_with("xoxb-")
        || (value.starts_with(AWS_ACCESS_KEY_PREFIX) && value.len() == 20)
        || (value.starts_with(JWT_PREFIX) && value.matches('.').count() == 2)
}

fn custom_credential_token(value: &str, token_prefixes: &[String]) -> bool {
    token_prefixes
        .iter()
        .any(|prefix| value.starts_with(prefix))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn redacts_nested_fields_and_credential_values() {
        let mut value =
            serde_json::json!({"nested":{"api_key":"secret"},"header":"Bearer abc","safe":"ok"});
        let metadata = AuditRedactor::default().redact(&mut value);
        assert_eq!(value["nested"]["api_key"], REDACTED);
        assert_eq!(value["header"], REDACTED);
        assert_eq!(value["safe"], "ok");
        assert_eq!(metadata.redacted_fields.len(), 2);
    }

    #[test]
    fn redacts_configured_json_pointer() {
        let mut value = serde_json::json!({"custom":{"private":"secret", "public":"ok"}});
        let redactor = AuditRedactor::new(
            AuditRedactionRules::default(),
            vec!["/custom/private".to_owned()],
            1024,
        );
        let metadata = redactor.redact(&mut value);
        assert_eq!(value["custom"]["private"], REDACTED);
        assert_eq!(value["custom"]["public"], "ok");
        assert!(
            metadata
                .redacted_fields
                .contains(&"/custom/private".to_owned())
        );
    }

    #[test]
    fn truncates_payload_after_redaction() {
        let mut value = serde_json::json!({"password":"secret", "large":"123456789"});
        let metadata =
            AuditRedactor::new(AuditRedactionRules::default(), Vec::new(), 8).redact(&mut value);
        assert_eq!(value, REDACTED);
        assert!(metadata.payload_truncated);
        assert!(metadata.redacted_fields.contains(&"/password".to_owned()));
    }

    #[test]
    fn redacts_common_token_formats() {
        for credential in [
            "ghp_1234567890",
            "github_pat_1234567890",
            "xoxb-123-456",
            "AKIA1234567890123456",
            "eyJheader.payload.signature",
        ] {
            let mut value = Value::String(credential.to_owned());
            AuditRedactor::default().redact(&mut value);
            assert_eq!(value, REDACTED);
        }
    }

    #[test]
    fn redacts_credential_shaped_string_field() {
        for original in [
            "Bearer evaluator-secret",
            "blocked: Bearer evaluator-secret",
            "rejected credential ghp_embedded-secret",
        ] {
            let mut explanation = original.to_owned();
            let metadata = AuditRedactor::default().redact_string(&mut explanation);
            assert_eq!(explanation, REDACTED);
            assert_eq!(metadata.redacted_fields, vec![String::new()]);
        }
    }

    #[test]
    fn does_not_redact_ordinary_embedded_prefix_text() {
        let mut explanation = "risk-based policy blocked the request".to_owned();
        let metadata = AuditRedactor::default().redact_string(&mut explanation);
        assert_eq!(explanation, "risk-based policy blocked the request");
        assert!(metadata.redacted_fields.is_empty());
    }

    #[test]
    fn configured_rules_replace_default_rules() {
        let redactor = AuditRedactor::new(
            AuditRedactionRules {
                include_defaults: false,
                sensitive_fields: vec!["private".to_owned()],
                credential_markers: vec!["credential ".to_owned()],
                token_prefixes: vec!["custom_".to_owned()],
            },
            Vec::new(),
            1024,
        );
        let mut value = serde_json::json!({
            "password": "visible",
            "private": "hidden",
            "marker": "Credential hidden",
            "prefix": "custom_hidden",
            "default_prefix": "ghp_visible"
        });

        redactor.redact(&mut value);

        assert_eq!(value["password"], "visible");
        assert_eq!(value["private"], REDACTED);
        assert_eq!(value["marker"], REDACTED);
        assert_eq!(value["prefix"], REDACTED);
        assert_eq!(value["default_prefix"], "ghp_visible");
    }

    #[test]
    fn configured_rules_extend_defaults() {
        let redactor = AuditRedactor::new(
            AuditRedactionRules {
                sensitive_fields: vec!["private".to_owned()],
                credential_markers: vec!["credential ".to_owned()],
                token_prefixes: vec!["custom_".to_owned()],
                ..AuditRedactionRules::default()
            },
            Vec::new(),
            1024,
        );
        let mut value = serde_json::json!({
            "password": "default field",
            "private": "custom field",
            "marker": "credential hidden",
            "default_prefix": "ghp_hidden",
            "custom_prefix": "custom_hidden"
        });

        redactor.redact(&mut value);

        for field in [
            "password",
            "private",
            "marker",
            "default_prefix",
            "custom_prefix",
        ] {
            assert_eq!(value[field], REDACTED);
        }
    }

    #[test]
    fn default_matchers_reject_lookalikes() {
        for ordinary in ["foobearer value", "akialias", "eyJoke"] {
            let mut value = Value::String(ordinary.to_owned());
            let metadata = AuditRedactor::default().redact(&mut value);
            assert_eq!(value, ordinary);
            assert!(metadata.redacted_fields.is_empty());
        }
    }

    #[test]
    fn empty_custom_entries_do_not_match_values() {
        let redactor = AuditRedactor::new(
            AuditRedactionRules {
                include_defaults: false,
                sensitive_fields: vec![String::new(), "  ".to_owned()],
                credential_markers: vec![String::new(), "  ".to_owned()],
                token_prefixes: vec![String::new(), "  ".to_owned()],
            },
            Vec::new(),
            1024,
        );
        let mut value = serde_json::json!({"field": "ordinary value"});

        let metadata = redactor.redact(&mut value);

        assert_eq!(value["field"], "ordinary value");
        assert!(metadata.redacted_fields.is_empty());
    }
}
