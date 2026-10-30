use http::{Response, StatusCode};
use wanaku_infra::audit::InMemoryAuditStore;
use wanaku_types::audit::{AUDIT_SCHEMA_VERSION, AuditDecision, AuditQuery, AuditStore};
use wanaku_types::http_response::{json_err, json_ok};

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum AuditRoute {
    Ingest,
    List,
    Get(String),
    Schema,
    Health,
    NotFound,
}

pub(crate) fn resolve(method: &str, path: &str) -> AuditRoute {
    if method == "POST" && matches!(path, "/api/v1/audit/semantic" | "/api/v1/audit/semantic/") {
        return AuditRoute::Ingest;
    }
    if method != "GET" {
        return AuditRoute::NotFound;
    }
    match path {
        "/api/v1/audit/events" | "/api/v1/audit/events/" => AuditRoute::List,
        "/api/v1/audit/schema" | "/api/v1/audit/schema/" => AuditRoute::Schema,
        "/api/v1/audit/health" | "/api/v1/audit/health/" => AuditRoute::Health,
        _ => path
            .strip_prefix("/api/v1/audit/events/")
            .filter(|id| !id.is_empty() && !id.contains('/'))
            .map_or(AuditRoute::NotFound, |id| AuditRoute::Get(id.to_owned())),
    }
}

pub(crate) fn handle(
    store: &InMemoryAuditStore,
    route: AuditRoute,
    query: Option<&str>,
) -> Response<Vec<u8>> {
    match route {
        AuditRoute::List => match parse_query(query) {
            Ok(query) => json_ok(&serde_json::json!(store.query(&query))),
            Err(message) => json_err(StatusCode::BAD_REQUEST, &message),
        },
        AuditRoute::Get(id) => store.get(&id).map_or_else(
            || json_err(StatusCode::NOT_FOUND, "audit event not found"),
            |event| json_ok(&serde_json::json!(event)),
        ),
        AuditRoute::Schema => json_ok(&serde_json::json!({"schema_version": AUDIT_SCHEMA_VERSION})),
        AuditRoute::Health => json_ok(&serde_json::json!(store.health())),
        AuditRoute::Ingest | AuditRoute::NotFound => {
            json_err(StatusCode::NOT_FOUND, "audit route not found")
        }
    }
}

pub(crate) fn ingest(
    store: &InMemoryAuditStore,
    enabled: bool,
    body: Option<&str>,
) -> Response<Vec<u8>> {
    use wanaku_types::semantic_audit::SemanticAdmissionError;
    if !enabled {
        return json_err(StatusCode::NOT_FOUND, "semantic audit ingestion disabled");
    }
    let body = body.unwrap_or_default();
    if body.len() > 65_536 {
        return json_err(
            StatusCode::PAYLOAD_TOO_LARGE,
            "semantic audit body exceeds 64 KiB",
        );
    }
    let event = match parse_semantic_event(body) {
        Ok(event) => event,
        Err(error) => return json_err(StatusCode::BAD_REQUEST, error),
    };
    match store.accept_semantic(event) {
        Ok(accepted) => json_ok(&serde_json::json!(accepted)),
        Err(SemanticAdmissionError::Conflict) => {
            json_err(StatusCode::CONFLICT, "source event ID conflict")
        }
        Err(SemanticAdmissionError::Capacity) => json_err(
            StatusCode::TOO_MANY_REQUESTS,
            "audit external capacity unavailable",
        ),
        Err(SemanticAdmissionError::Unavailable) => {
            json_err(StatusCode::SERVICE_UNAVAILABLE, "audit storage unavailable")
        }
    }
}

fn parse_semantic_event(body: &str) -> Result<wanaku_types::audit::AuditEvent, &'static str> {
    use wanaku_types::semantic_audit::SemanticAuditEnvelope;
    let raw = serde_json::from_str::<serde_json::Value>(body)
        .map_err(|_| "invalid semantic audit envelope")?;
    if contains_null(&raw) {
        return Err("null audit fields are not supported");
    }
    let envelope = serde_json::from_str::<SemanticAuditEnvelope>(body)
        .map_err(|_| "invalid semantic audit envelope")?;
    envelope.validate()?;
    envelope
        .into_event()
        .map_err(|_| "invalid semantic audit record")
}

fn contains_null(value: &serde_json::Value) -> bool {
    match value {
        serde_json::Value::Null => true,
        serde_json::Value::Array(values) => values.iter().any(contains_null),
        serde_json::Value::Object(values) => values.values().any(contains_null),
        _ => false,
    }
}

fn parse_query(raw: Option<&str>) -> Result<AuditQuery, String> {
    let mut query = AuditQuery::default();
    for (key, value) in form_urlencoded::parse(raw.unwrap_or_default().as_bytes()) {
        let invalid = || format!("invalid {key}: {value}");
        match key.as_ref() {
            "source_instance_id" => query.source_instance_id = Some(value.into_owned()),
            "source_event_id" => query.source_event_id = Some(value.into_owned()),
            "category" => query.category = Some(parse_category(&value).ok_or_else(invalid)?),
            "from" => query.from = Some(value.into_owned()),
            "to" => query.to = Some(value.into_owned()),
            "namespace" => query.namespace = Some(value.into_owned()),
            "actor" => query.actor = Some(value.into_owned()),
            "operation" => query.operation = Some(value.into_owned()),
            "target" => query.target = Some(value.into_owned()),
            "reason_code" => query.reason_code = Some(value.into_owned()),
            "correlation_id" => query.correlation_id = Some(value.into_owned()),
            "offset" => query.offset = value.parse().map_err(|_| invalid())?,
            "limit" => query.limit = value.parse().map_err(|_| invalid())?,
            "decision" => query.decision = Some(parse_decision(&value).ok_or_else(invalid)?),
            _ => {}
        }
    }
    Ok(query)
}

fn parse_category(value: &str) -> Option<wanaku_types::audit::AuditCategory> {
    match value {
        "semantic" => Some(wanaku_types::audit::AuditCategory::Semantic),
        "decision" => Some(wanaku_types::audit::AuditCategory::Decision),
        "administrative" => Some(wanaku_types::audit::AuditCategory::Administrative),
        _ => None,
    }
}

fn parse_decision(value: &str) -> Option<AuditDecision> {
    match value {
        "observed" => Some(AuditDecision::Observed),
        "allow" => Some(AuditDecision::Allow),
        "block" => Some(AuditDecision::Block),
        "warn" => Some(AuditDecision::Warn),
        "reject_malformed" => Some(AuditDecision::RejectMalformed),
        "error" => Some(AuditDecision::Error),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_types::TOOLS_CALL;
    use wanaku_types::audit::{AuditCategory, AuditEvent};
    fn envelope(category: &str, event_id: &str) -> serde_json::Value {
        serde_json::json!({"source":{"instance_id":"runtime-1","forward_name":"support","namespace":"default","catalog_revision":"r1","catalog_digest":"digest1"},"record":{"schemaVersion":1,"eventId":event_id,"timestamp":"2026-10-12T12:00:00Z","category":category}})
    }
    #[test]
    fn semantic_ingestion_is_opt_in_and_size_bounded() {
        let store = InMemoryAuditStore::new(10);
        let body = envelope("evaluation", "event-1").to_string();
        assert_eq!(
            ingest(&store, false, Some(&body)).status(),
            StatusCode::NOT_FOUND
        );
        assert_eq!(
            ingest(&store, true, Some(&"x".repeat(65_537))).status(),
            StatusCode::PAYLOAD_TOO_LARGE
        );
        assert_eq!(store.query(&AuditQuery::default()).total, 0);
    }
    #[test]
    fn validates_camel_fields_before_acceptance() {
        let store = InMemoryAuditStore::new(10);
        for (field, value) in [
            ("schemaVersion", serde_json::json!(2)),
            ("category", serde_json::json!("allow")),
            ("input", serde_json::json!("secret")),
            ("inputRedacted", serde_json::json!(true)),
            ("unknown", serde_json::json!("value")),
            ("status", serde_json::json!(true)),
            ("status", serde_json::Value::Null),
            ("durationNanos", serde_json::json!(-1)),
            ("eventId", serde_json::json!(" ")),
            ("timestamp", serde_json::json!("invalid")),
            ("provider", serde_json::json!(" ")),
        ] {
            let mut body = envelope("evaluation", "event-1");
            body["record"][field] = value;
            assert_eq!(
                ingest(&store, true, Some(&body.to_string())).status(),
                StatusCode::BAD_REQUEST,
                "field {field}"
            );
        }
        let mut body = envelope("evaluation", "event-1");
        body["source"]["trusted"] = serde_json::json!(true);
        assert_eq!(
            ingest(&store, true, Some(&body.to_string())).status(),
            StatusCode::BAD_REQUEST
        );
        assert_eq!(store.query(&AuditQuery::default()).total, 0);
    }
    #[test]
    fn preserves_three_categories_typed_results_and_evidence() {
        let store = InMemoryAuditStore::new(10);
        let values = serde_json::json!([true, 2.5, "billing", ["billing", "support"]]);
        for (index, value) in values.as_array().into_iter().flatten().enumerate() {
            for category in ["evaluation", "decision", "request"] {
                let mut body = envelope(category, &format!("{category}-{index}"));
                body["record"]["result"] = serde_json::json!({"value":value,"probability":0.7});
                body["record"]["evidence"] = serde_json::json!(["evaluation-1"]);
                assert_eq!(
                    ingest(&store, true, Some(&body.to_string())).status(),
                    StatusCode::OK
                );
                let page = store.query(&AuditQuery {
                    source_event_id: Some(format!("{category}-{index}")),
                    ..AuditQuery::default()
                });
                assert_eq!(page.total, 1);
                assert_eq!(page.events[0].decision, AuditDecision::Observed);
                assert_eq!(
                    &page.events[0].attributes["semantic"]["result"]["value"],
                    value
                );
                assert!(page.events[0].source.as_ref().is_some_and(
                    |source| source.producer_asserted && source.evidence == ["evaluation-1"]
                ));
            }
        }
    }
    #[test]
    fn retries_are_atomic_and_conflicting_content_is_rejected() {
        let store = InMemoryAuditStore::new(10);
        let mut body = envelope("evaluation", "event-1");
        let first = ingest(&store, true, Some(&body.to_string()));
        let repeated = ingest(&store, true, Some(&body.to_string()));
        let first_json: serde_json::Value =
            serde_json::from_slice(first.body()).unwrap_or_default();
        let repeated_json: serde_json::Value =
            serde_json::from_slice(repeated.body()).unwrap_or_default();
        assert_eq!(
            first_json["data"]["event_id"],
            repeated_json["data"]["event_id"]
        );
        assert_eq!(repeated_json["data"]["duplicate"], true);
        body["record"]["status"] = serde_json::json!("failed");
        assert_eq!(
            ingest(&store, true, Some(&body.to_string())).status(),
            StatusCode::CONFLICT
        );
        assert_eq!(store.query(&AuditQuery::default()).total, 1);
    }
    #[test]
    fn maps_explicit_camel_context_into_common_query_fields() {
        let store = InMemoryAuditStore::new(10);
        let mut body = envelope("decision", "event-1");
        body["record"]["correlationId"] = serde_json::json!("app-correlation");
        body["record"]["requestId"] = serde_json::json!("app-request");
        body["record"]["target"] = serde_json::json!("billing");
        body["record"]["expert"] = serde_json::json!("supportExpert");
        body["record"]["policyVersion"] = serde_json::json!("policy-3");
        body["record"]["durationNanos"] = serde_json::json!(2_500_000);
        body["record"]["reasonCode"] = serde_json::json!("selected");
        assert_eq!(
            ingest(&store, true, Some(&body.to_string())).status(),
            StatusCode::OK
        );
        let response = handle(
            &store,
            AuditRoute::List,
            Some("correlation_id=app-correlation&target=billing&reason_code=selected"),
        );
        let response: serde_json::Value =
            serde_json::from_slice(response.body()).unwrap_or_default();
        assert_eq!(response["data"]["total"], 1);
        let event = &response["data"]["events"][0];
        assert_eq!(event["request_id"], "app-request");
        assert_eq!(event["evaluator"], "supportExpert");
        assert_eq!(event["policy_revision"], "policy-3");
        assert_eq!(event["duration_ms"], 2);
        assert!(event.get("actor").is_none());
        assert!(event.get("workload").is_none());
    }
    #[test]
    fn camel_execution_identifiers_do_not_replace_application_correlation() {
        let store = InMemoryAuditStore::new(10);
        let mut body = envelope("evaluation", "event-1");
        body["record"]["exchangeId"] = serde_json::json!("exchange-1");
        body["record"]["invocationId"] = serde_json::json!("invocation-1");
        assert_eq!(
            ingest(&store, true, Some(&body.to_string())).status(),
            StatusCode::OK
        );
        let page = store.query(&AuditQuery::default());
        assert_eq!(page.events[0].correlation_id, page.events[0].event_id);
        assert!(page.events[0].request_id.is_none());
    }

    #[test]
    fn accepts_camel_identifiers_and_namespace_without_rewriting_references() {
        let store = InMemoryAuditStore::new(10);
        let mut body = envelope("decision", "event#1/@=x");
        body["source"]["namespace"] = serde_json::json!("tenant west");
        body["record"]["evidence"] = serde_json::json!(["decision#1/@=x"]);
        assert_eq!(
            ingest(&store, true, Some(&body.to_string())).status(),
            StatusCode::OK
        );
        let page = store.query(&AuditQuery::default());
        assert!(
            page.events[0]
                .source
                .as_ref()
                .is_some_and(|source| source.namespace == "tenant west"
                    && source.event_id == "event#1/@=x"
                    && source.evidence == ["decision#1/@=x"])
        );
        for value in [" ", "bad\nidentifier"] {
            body["record"]["eventId"] = serde_json::json!(value);
            assert_eq!(
                ingest(&store, true, Some(&body.to_string())).status(),
                StatusCode::BAD_REQUEST
            );
        }
    }

    #[test]
    fn semantic_content_is_redacted_but_source_references_survive() {
        let store = InMemoryAuditStore::new(10);
        let mut body = envelope("evaluation", "ghp_source-reference");
        body["record"]["result"] = serde_json::json!({"value":"Bearer secret"});
        body["record"]["evidence"] = serde_json::json!(["ghp_evidence-reference"]);
        assert_eq!(
            ingest(&store, true, Some(&body.to_string())).status(),
            StatusCode::OK
        );
        let page = store.query(&AuditQuery::default());
        assert_eq!(
            page.events[0].attributes["semantic"]["result"]["value"],
            "[REDACTED]"
        );
        assert!(
            page.events[0]
                .source
                .as_ref()
                .is_some_and(|source| source.event_id == "ghp_source-reference"
                    && source.evidence == ["ghp_evidence-reference"])
        );
    }

    #[test]
    fn resolves_supported_routes() {
        assert_eq!(
            resolve("GET", "/api/v1/audit/events/id"),
            AuditRoute::Get("id".to_owned())
        );
        assert_eq!(
            resolve("POST", "/api/v1/audit/events"),
            AuditRoute::NotFound
        );
    }

    #[test]
    fn list_rejects_invalid_filter_values() {
        let store = InMemoryAuditStore::new(10);
        for query in ["decision=bogus", "category=bogus", "limit=ten", "offset=-1"] {
            let response = handle(&store, AuditRoute::List, Some(query));
            assert_eq!(response.status(), StatusCode::BAD_REQUEST, "{query}");
        }
        let response = handle(&store, AuditRoute::List, Some("decision=block&limit=5"));
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[test]
    fn query_parser_decodes_browser_encoded_values() {
        let query = parse_query(Some(
            "operation=tools%2Fcall&namespace=finance+team&from=2026-09-14T12%3A00%3A00Z",
        ))
        .unwrap_or_default();

        assert_eq!(query.operation.as_deref(), Some(TOOLS_CALL));
        assert_eq!(query.namespace.as_deref(), Some("finance team"));
        assert_eq!(query.from.as_deref(), Some("2026-09-14T12:00:00Z"));
    }

    #[test]
    fn lists_filters_and_retrieves_events() {
        let store = InMemoryAuditStore::new(10);
        let mut event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Block,
            TOOLS_CALL,
            "policy_denied",
            "Denied",
        );
        event.namespace = Some("finance".to_owned());
        let event_id = event.event_id.clone();
        store.record(event);

        let response = handle(
            &store,
            AuditRoute::List,
            Some("namespace=finance&decision=block&limit=10"),
        );
        let body = serde_json::from_slice::<serde_json::Value>(response.body());
        assert!(body.is_ok());
        if let Ok(body) = body {
            assert_eq!(body["data"]["total"], 1);
            assert_eq!(body["data"]["events"][0]["reason_code"], "policy_denied");
        }

        let response = handle(&store, AuditRoute::Get(event_id), None);
        assert_eq!(response.status(), StatusCode::OK);
    }
}
