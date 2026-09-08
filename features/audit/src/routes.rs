use http::{Response, StatusCode};
use wanaku_types::audit::{
    AUDIT_SCHEMA_VERSION, AuditDecision, AuditQuery, AuditStore, InMemoryAuditStore,
};
use wanaku_types::http_response::{json_err, json_ok};

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum AuditRoute {
    List,
    Get(String),
    Schema,
    Health,
    NotFound,
}

pub(crate) fn resolve(method: &str, path: &str) -> AuditRoute {
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
        AuditRoute::List => json_ok(&serde_json::json!(store.query(&parse_query(query)))),
        AuditRoute::Get(id) => store.get(&id).map_or_else(
            || json_err(StatusCode::NOT_FOUND, "audit event not found"),
            |event| json_ok(&serde_json::json!(event)),
        ),
        AuditRoute::Schema => json_ok(&serde_json::json!({"schema_version": AUDIT_SCHEMA_VERSION})),
        AuditRoute::Health => json_ok(&serde_json::json!(store.health())),
        AuditRoute::NotFound => json_err(StatusCode::NOT_FOUND, "audit route not found"),
    }
}

fn parse_query(raw: Option<&str>) -> AuditQuery {
    let mut query = AuditQuery::default();
    for (key, value) in raw
        .unwrap_or_default()
        .split('&')
        .filter_map(|part| part.split_once('='))
    {
        match key {
            "from" => query.from = Some(value.to_owned()),
            "to" => query.to = Some(value.to_owned()),
            "namespace" => query.namespace = Some(value.to_owned()),
            "actor" => query.actor = Some(value.to_owned()),
            "operation" => query.operation = Some(value.to_owned()),
            "target" => query.target = Some(value.to_owned()),
            "reason_code" => query.reason_code = Some(value.to_owned()),
            "correlation_id" => query.correlation_id = Some(value.to_owned()),
            "offset" => query.offset = value.parse().unwrap_or_default(),
            "limit" => query.limit = value.parse().unwrap_or_default(),
            "decision" => {
                query.decision = match value {
                    "allow" => Some(AuditDecision::Allow),
                    "block" => Some(AuditDecision::Block),
                    "warn" => Some(AuditDecision::Warn),
                    "reject_malformed" => Some(AuditDecision::RejectMalformed),
                    "error" => Some(AuditDecision::Error),
                    _ => None,
                }
            }
            _ => {}
        }
    }
    query
}

#[cfg(test)]
mod tests {
    use super::*;
    use wanaku_types::audit::{AuditCategory, AuditEvent};
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
    fn lists_filters_and_retrieves_events() {
        let store = InMemoryAuditStore::new(10);
        let mut event = AuditEvent::new(
            AuditCategory::Decision,
            AuditDecision::Block,
            "tools/call",
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
