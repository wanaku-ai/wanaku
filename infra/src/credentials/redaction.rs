//! Redaction of brokered credential material from upstream output.
//!
//! A [`CredentialRedactor`] holds every secret byte-string one brokered call
//! could echo back: the raw resolved secret material and the full injected header
//! value (for example `Bearer <secret>`). It strips each occurrence from upstream
//! text and JSON before that content reaches the agent or the logs.
//!
//! Both injection channels build a redactor from the same [`BrokeredCredential`]:
//! the request filter pipeline (tool calls, resource reads, prompt gets) and the
//! forward discovery path (discovered tools, resources, prompts, and server
//! identity). Never serialize or log the patterns a redactor holds.

use serde_json::Value;

use super::broker::BrokeredCredential;

/// The placeholder that replaces any brokered secret found in upstream output.
const REDACTION_PLACEHOLDER: &str = "<redacted>";

/// Redacts brokered credential material from upstream text before it reaches the
/// agent or the logs.
///
/// An empty redactor (no binding, or nothing to inject) is a no-op. The default
/// value is empty.
#[derive(Clone, Default)]
pub struct CredentialRedactor {
    /// Secret patterns to strip, ordered longest-first so a longer match wins
    /// over a shorter substring of it.
    patterns: Vec<String>,
}

impl std::fmt::Debug for CredentialRedactor {
    /// Never print the patterns: they are the secret material this type exists to
    /// strip. Report only the count so debug output stays useful without leaking.
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CredentialRedactor")
            .field(
                "patterns",
                &format_args!("<{} redacted>", self.patterns.len()),
            )
            .finish()
    }
}

impl CredentialRedactor {
    /// Build a redactor from resolved secret patterns.
    ///
    /// Empty patterns are dropped and the rest are ordered longest-first so an
    /// overlapping shorter secret cannot leave a fragment behind. Prefer
    /// [`from_brokered`](Self::from_brokered) for production code; this
    /// constructor is public mainly so redaction behavior can be unit-tested
    /// from other crates.
    #[must_use]
    pub fn from_patterns(mut patterns: Vec<String>) -> Self {
        patterns.retain(|p| !p.is_empty());
        patterns.sort_by_key(|p| std::cmp::Reverse(p.len()));
        Self { patterns }
    }

    /// Build a redactor from a brokered credential.
    ///
    /// Collects both the full injected header value (for example `Bearer
    /// <secret>`) and the raw resolved secret material, so an upstream that
    /// echoes either form is redacted.
    #[must_use]
    pub fn from_brokered(brokered: &BrokeredCredential) -> Self {
        let mut patterns: Vec<String> = Vec::new();
        for header in &brokered.headers {
            patterns
                .push(String::from_utf8_lossy(header.expose_value().expose_bytes()).into_owned());
        }
        for material in &brokered.redaction_material {
            patterns.push(String::from_utf8_lossy(material.expose_bytes()).into_owned());
        }
        Self::from_patterns(patterns)
    }

    /// Whether the redactor holds no patterns (a no-op).
    #[must_use]
    pub const fn is_empty(&self) -> bool {
        self.patterns.is_empty()
    }

    /// Replace every occurrence of a known secret with `<redacted>`.
    ///
    /// Returns the input unchanged when there is nothing to redact.
    #[must_use]
    pub fn redact(&self, input: &str) -> String {
        let mut out = input.to_owned();
        for pattern in &self.patterns {
            if out.contains(pattern.as_str()) {
                out = out.replace(pattern.as_str(), REDACTION_PLACEHOLDER);
            }
        }
        out
    }

    /// Recursively redact every scalar leaf of a JSON value.
    ///
    /// The upstream controls every part of the value it echoes back, so the
    /// redactor treats string values, object keys, and numeric scalars as
    /// reflection channels. String values (including those nested in arrays and
    /// objects) are redacted. Object keys are redacted because an upstream can
    /// place the secret in a property name (for example a tool `inputSchema`
    /// property). Numbers are redacted when their textual form contains the
    /// secret; a number that matches becomes the placeholder string, and a number
    /// that does not match keeps its numeric type. Booleans and null cannot carry
    /// a secret and are left unchanged. Redacting the decoded string (rather than
    /// the serialized envelope) matches the raw secret even when JSON escaping
    /// would otherwise hide it.
    #[must_use]
    pub fn redact_json(&self, value: &Value) -> Value {
        if self.patterns.is_empty() {
            return value.clone();
        }
        match value {
            Value::String(s) => Value::String(self.redact(s)),
            Value::Number(n) => {
                let text = n.to_string();
                let redacted = self.redact(&text);
                if redacted == text {
                    value.clone()
                } else {
                    Value::String(redacted)
                }
            }
            Value::Array(items) => {
                Value::Array(items.iter().map(|v| self.redact_json(v)).collect())
            }
            Value::Object(map) => Value::Object(
                map.iter()
                    .map(|(k, v)| (self.redact(k), self.redact_json(v)))
                    .collect(),
            ),
            other => other.clone(),
        }
    }

    /// Redact every value in a slice of JSON values.
    #[must_use]
    pub fn redact_json_each(&self, values: &[Value]) -> Vec<Value> {
        values.iter().map(|v| self.redact_json(v)).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn redactor_is_noop_when_empty() {
        let redactor = CredentialRedactor::default();
        assert!(redactor.is_empty());
        assert_eq!(redactor.redact("plain text s3cr3t"), "plain text s3cr3t");
    }

    #[test]
    fn redactor_drops_empty_patterns() {
        // An empty pattern must never match the whole input.
        let redactor = CredentialRedactor::from_patterns(vec![String::new()]);
        assert!(redactor.is_empty());
        assert_eq!(redactor.redact("unchanged"), "unchanged");
    }

    #[test]
    fn redactor_prefers_longer_overlapping_patterns() {
        // The longer header value is redacted before its secret substring, so no
        // fragment of the wrapper is left behind.
        let redactor = CredentialRedactor::from_patterns(vec![
            "s3cr3t".to_owned(),
            "Bearer s3cr3t".to_owned(),
        ]);
        assert_eq!(redactor.redact("Bearer s3cr3t"), "<redacted>");
    }

    #[test]
    fn redactor_strips_all_occurrences() {
        let redactor = CredentialRedactor::from_patterns(vec!["s3cr3t".to_owned()]);
        assert_eq!(
            redactor.redact("s3cr3t and again s3cr3t"),
            "<redacted> and again <redacted>"
        );
    }

    #[test]
    fn redact_json_walks_nested_string_leaves() {
        let redactor = CredentialRedactor::from_patterns(vec!["s3cr3t".to_owned()]);
        let value = serde_json::json!({
            "a": "has s3cr3t",
            "b": ["nested s3cr3t", 42, true],
            "c": {"d": "deep s3cr3t"},
            "n": 7
        });

        let redacted = redactor.redact_json(&value);

        assert_eq!(redacted.pointer("/a").unwrap(), "has <redacted>");
        assert_eq!(redacted.pointer("/b/0").unwrap(), "nested <redacted>");
        // Non-string leaves are preserved.
        assert_eq!(redacted.pointer("/b/1").unwrap(), 42);
        assert_eq!(redacted.pointer("/c/d").unwrap(), "deep <redacted>");
        assert_eq!(redacted.pointer("/n").unwrap(), 7);
    }

    #[test]
    fn redact_json_is_noop_when_empty() {
        let redactor = CredentialRedactor::default();
        let value = serde_json::json!({"text": "plain s3cr3t"});
        assert_eq!(redactor.redact_json(&value), value);
    }

    #[test]
    fn redact_json_redacts_object_keys() {
        // An upstream can place the secret in a property name (for example a tool
        // inputSchema property), so keys are a reflection channel too.
        let redactor = CredentialRedactor::from_patterns(vec!["s3cr3t".to_owned()]);
        let value = serde_json::json!({
            "properties": {"s3cr3t": {"type": "string"}}
        });

        let redacted = redactor.redact_json(&value);

        assert!(redacted.pointer("/properties/s3cr3t").is_none());
        assert_eq!(
            redacted.pointer("/properties/<redacted>/type").unwrap(),
            "string"
        );
    }

    #[test]
    fn redact_json_redacts_matching_number() {
        // A numeric secret echoed as a JSON number must not slip through.
        let redactor = CredentialRedactor::from_patterns(vec!["12345678".to_owned()]);
        let value = serde_json::json!({"pin": 12345678, "keep": 42});

        let redacted = redactor.redact_json(&value);

        // The matching number becomes the placeholder string.
        assert_eq!(redacted.pointer("/pin").unwrap(), "<redacted>");
        // A non-matching number keeps its numeric type.
        assert_eq!(redacted.pointer("/keep").unwrap(), 42);
    }

    #[test]
    fn redact_json_each_redacts_every_item() {
        let redactor = CredentialRedactor::from_patterns(vec!["s3cr3t".to_owned()]);
        let values = vec![
            serde_json::json!({"text": "a s3cr3t"}),
            serde_json::json!("bare s3cr3t"),
        ];

        let redacted = redactor.redact_json_each(&values);

        assert_eq!(redacted[0].pointer("/text").unwrap(), "a <redacted>");
        assert_eq!(redacted[1], serde_json::json!("bare <redacted>"));
    }
}
