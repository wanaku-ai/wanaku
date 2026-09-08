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

#[derive(Debug, Clone)]
pub struct AuditRedactor {
    sensitive_fields: Vec<String>,
    json_pointers: Vec<String>,
    payload_limit: usize,
}

impl AuditRedactor {
    #[must_use]
    pub fn new(
        sensitive_fields: Vec<String>,
        json_pointers: Vec<String>,
        payload_limit: usize,
    ) -> Self {
        let mut fields = DEFAULT_SENSITIVE_FIELDS
            .iter()
            .map(|value| (*value).to_owned())
            .collect::<Vec<_>>();
        fields.extend(
            sensitive_fields
                .into_iter()
                .map(|value| value.to_ascii_lowercase()),
        );
        Self {
            sensitive_fields: fields,
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
        redact_value(
            value,
            "",
            &self.sensitive_fields,
            &mut metadata.redacted_fields,
        );
        let encoded_len = serde_json::to_vec(value).map_or(0, |bytes| bytes.len());
        if encoded_len > self.payload_limit {
            *value = Value::String(REDACTED.to_owned());
            metadata.payload_truncated = true;
        }
        metadata
    }
}

impl Default for AuditRedactor {
    fn default() -> Self {
        Self::new(Vec::new(), Vec::new(), 16_384)
    }
}

fn redact_value(value: &mut Value, path: &str, fields: &[String], redacted: &mut Vec<String>) {
    match value {
        Value::Object(object) => {
            for (key, child) in object {
                let child_path = format!("{path}/{key}");
                if fields.iter().any(|field| key.eq_ignore_ascii_case(field)) {
                    *child = Value::String(REDACTED.to_owned());
                    redacted.push(child_path);
                } else {
                    redact_value(child, &child_path, fields, redacted);
                }
            }
        }
        Value::Array(array) => {
            for (index, child) in array.iter_mut().enumerate() {
                redact_value(child, &format!("{path}/{index}"), fields, redacted);
            }
        }
        Value::String(text) if credential_shaped(text) => {
            *text = REDACTED.to_owned();
            redacted.push(path.to_owned());
        }
        _ => {}
    }
}

fn credential_shaped(value: &str) -> bool {
    let lower = value.to_ascii_lowercase();
    lower.starts_with("bearer ")
        || lower.starts_with("basic ")
        || lower.starts_with("sk-")
        || lower.starts_with("ghp_")
        || lower.starts_with("github_pat_")
        || lower.starts_with("xoxb-")
        || (value.starts_with("AKIA") && value.len() == 20)
        || (value.starts_with("eyJ") && value.matches('.').count() == 2)
        || lower.contains("client_secret=")
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
        let redactor = AuditRedactor::new(Vec::new(), vec!["/custom/private".to_owned()], 1024);
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
        let metadata = AuditRedactor::new(Vec::new(), Vec::new(), 8).redact(&mut value);
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
}
