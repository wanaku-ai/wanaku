use crate::audit::{AuditCategory, AuditDecision, AuditEvent};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SemanticAuditEnvelope {
    pub source: SemanticSource,
    pub record: SemanticRecord,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SemanticSource {
    pub instance_id: String,
    pub forward_name: String,
    pub namespace: String,
    pub catalog_revision: String,
    pub catalog_digest: String,
}
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct AuditSource {
    pub instance_id: String,
    pub forward_name: String,
    pub namespace: String,
    pub catalog_revision: String,
    pub catalog_digest: String,
    pub event_id: String,
    pub timestamp: String,
    pub evidence: Vec<String>,
    pub producer_asserted: bool,
    pub fingerprint: String,
}
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
pub struct SemanticRecord {
    pub schema_version: u8,
    pub event_id: String,
    pub timestamp: String,
    pub category: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub invocation_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub origin: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub request_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub batch_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub definition: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub target: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub expert: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub operation: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub context_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub exchange_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub route_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub status: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason_code: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub result_omitted: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub action: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub namespace: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub correlation_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub policy_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub policy_version: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub rule: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub provider: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub model: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub revision: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub input_omitted: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub breadcrumb_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub duration_nanos: Option<u64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub semantics: Option<BTreeMap<String, String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub result: Option<SemanticResult>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub evidence: Option<Vec<String>>,
}
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SemanticResult {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub value: Option<Value>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub probability: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub confidence: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub probabilities: Option<BTreeMap<String, f64>>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct SemanticAuditAcceptance {
    pub status: String,
    pub event_id: String,
    pub duplicate: bool,
}
#[derive(Debug, thiserror::Error)]
pub enum SemanticAdmissionError {
    #[error("audit storage unavailable")]
    Unavailable,
    #[error("source event ID conflicts with retained record")]
    Conflict,
    #[error("audit external capacity unavailable")]
    Capacity,
}
impl SemanticAuditEnvelope {
    pub fn validate(&self) -> Result<(), &'static str> {
        let record = &self.record;
        if record.schema_version != 1
            || !matches!(
                record.category.as_str(),
                "evaluation" | "decision" | "request"
            )
            || record.event_id.trim().is_empty()
        {
            return Err("invalid semantic audit record");
        }
        for timestamp in [Some(&record.timestamp), record.started_at.as_ref()]
            .into_iter()
            .flatten()
        {
            if time::OffsetDateTime::parse(
                timestamp,
                &time::format_description::well_known::Rfc3339,
            )
            .is_err()
            {
                return Err("invalid audit timestamp");
            }
        }
        let value = serde_json::to_value(self).map_err(|_| "invalid audit envelope")?;
        validate_bounds(&value, 0)?;
        validate_source(&self.source, record)?;
        validate_record_metadata(record)?;
        record.result.as_ref().map_or(Ok(()), validate_result)
    }
    pub fn into_event(self) -> Result<AuditEvent, serde_json::Error> {
        let canonical = serde_json::to_value(&self)?;
        let fingerprint = format!("{:x}", Sha256::digest(serde_json::to_vec(&canonical)?));
        let record_value = serde_json::to_value(&self.record)?;
        let mut event = AuditEvent::new(
            AuditCategory::Semantic,
            AuditDecision::Observed,
            self.record
                .operation
                .clone()
                .unwrap_or_else(|| self.record.category.clone()),
            "semantic_observed",
            "Producer-reported semantic observation",
        );
        set_record_context(&mut event, &self.record);
        event.protocol = "camel-semantic".to_owned();
        event.namespace = Some(self.source.namespace.clone());
        event.attributes.insert("semantic".to_owned(), record_value);
        event.source = Some(AuditSource {
            instance_id: self.source.instance_id,
            forward_name: self.source.forward_name,
            namespace: self.source.namespace,
            catalog_revision: self.source.catalog_revision,
            catalog_digest: self.source.catalog_digest,
            event_id: self.record.event_id,
            timestamp: self.record.timestamp,
            evidence: self.record.evidence.unwrap_or_default(),
            producer_asserted: true,
            fingerprint,
        });
        Ok(event)
    }
}
fn set_record_context(event: &mut AuditEvent, record: &SemanticRecord) {
    if let Some(correlation_id) = &record.correlation_id {
        event.correlation_id.clone_from(correlation_id);
    }
    event.request_id.clone_from(&record.request_id);
    event.target.clone_from(&record.target);
    event.evaluator.clone_from(&record.expert);
    event.policy_revision.clone_from(&record.policy_version);
    event.duration_ms = record.duration_nanos.map(|duration| duration / 1_000_000);
    if let Some(reason_code) = &record.reason_code {
        event.reason_code.clone_from(reason_code);
    }
}

fn validate_source(source: &SemanticSource, record: &SemanticRecord) -> Result<(), &'static str> {
    for text in [
        &source.instance_id,
        &source.forward_name,
        &source.namespace,
        &source.catalog_revision,
        &source.catalog_digest,
        &record.event_id,
    ] {
        if text.trim().is_empty() {
            return Err("invalid audit source identifier");
        }
    }
    Ok(())
}
fn validate_record_metadata(record: &SemanticRecord) -> Result<(), &'static str> {
    for identity in [&record.provider, &record.model, &record.revision]
        .into_iter()
        .flatten()
    {
        if identity.trim().is_empty() {
            return Err("invalid producer identity");
        }
    }
    if record.input_omitted.is_some() && record.category != "evaluation" {
        return Err("invalid input omission");
    }
    if let Some(evidence) = &record.evidence {
        for id in evidence {
            if id.trim().is_empty() {
                return Err("invalid evidence identifier");
            }
        }
    }
    if record.semantics.as_ref().is_some_and(|semantics| {
        semantics.keys().any(|key| {
            !matches!(
                key.as_str(),
                "resultType" | "meaning" | "probabilityMeaning" | "confidenceMeaning"
            )
        })
    }) {
        return Err("invalid audit semantics");
    }
    Ok(())
}
fn validate_bounds(value: &Value, depth: usize) -> Result<(), &'static str> {
    match value {
        Value::String(text) if text.chars().count() > 256 || text.chars().any(char::is_control) => {
            Err("audit text exceeds bounds")
        }
        Value::Array(values) if values.len() <= 100 && depth <= 4 => values
            .iter()
            .try_for_each(|value| validate_bounds(value, depth + 1)),
        Value::Object(values) if values.len() <= 100 && depth <= 4 => {
            values.iter().try_for_each(|(key, value)| {
                validate_bounds(&Value::String(key.clone()), depth + 1)?;
                validate_bounds(value, depth + 1)
            })
        }
        Value::Array(_) | Value::Object(_) => Err("audit collection exceeds bounds"),
        _ => Ok(()),
    }
}
fn validate_result(result: &SemanticResult) -> Result<(), &'static str> {
    if let Some(value) = &result.value {
        match value {
            Value::Bool(_) | Value::Number(_) | Value::String(_) => {}
            Value::Array(labels) if labels.iter().all(Value::is_string) => {}
            _ => return Err("invalid audit result value"),
        }
    }
    for probability in result
        .probability
        .iter()
        .chain(result.confidence.iter())
        .chain(result.probabilities.iter().flat_map(|map| map.values()))
    {
        if !probability.is_finite() || !(0.0..=1.0).contains(probability) {
            return Err("invalid audit probability");
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn envelope(record: &Value) -> Result<SemanticAuditEnvelope, serde_json::Error> {
        serde_json::from_value(
            serde_json::json!({"source":{"instance_id":"runtime-1","forward_name":"support","namespace":"default","catalog_revision":"r1","catalog_digest":"digest1"},"record":record}),
        )
    }
    fn record() -> Value {
        serde_json::json!({"schemaVersion":1,"eventId":"event-1","timestamp":"2026-10-12T12:00:00Z","category":"evaluation"})
    }
    #[test]
    fn result_validation_rejects_invalid_types_ranges_and_bounds() {
        for result in [
            serde_json::json!({"value":{}}),
            serde_json::json!({"value":[true]}),
            serde_json::json!({"value":"x".repeat(257)}),
            serde_json::json!({"probability":-0.1}),
            serde_json::json!({"confidence":1.1}),
            serde_json::json!({"probabilities":{"billing":2.0}}),
            serde_json::json!({"value":vec!["label";101]}),
        ] {
            let mut candidate = record();
            candidate["result"] = result;
            assert!(envelope(&candidate).is_ok_and(|envelope| envelope.validate().is_err()));
        }
    }
    #[test]
    fn evidence_and_semantics_validation_are_bounded() {
        for (field, value) in [
            ("evidence", serde_json::json!([""])),
            ("evidence", serde_json::json!(vec!["id"; 101])),
            ("semantics", serde_json::json!({"unknown":"value"})),
            ("semantics", serde_json::json!({"resultType":"x\n"})),
            ("startedAt", serde_json::json!("invalid")),
        ] {
            let mut candidate = record();
            candidate[field] = value;
            assert!(envelope(&candidate).is_ok_and(|envelope| envelope.validate().is_err()));
        }
    }
    #[test]
    fn fingerprint_is_stable_across_json_key_order() {
        let first = envelope(&record()).and_then(SemanticAuditEnvelope::into_event);
        let second = envelope(&serde_json::json!({"category":"evaluation","timestamp":"2026-10-12T12:00:00Z","eventId":"event-1","schemaVersion":1})).and_then(SemanticAuditEnvelope::into_event);
        assert!(first.is_ok());
        assert!(second.is_ok());
        if let (Ok(first), Ok(second)) = (first, second) {
            assert_eq!(
                first.source.map(|source| source.fingerprint),
                second.source.map(|source| source.fingerprint)
            );
        }
    }
}
