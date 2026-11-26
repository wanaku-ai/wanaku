use std::{
    io::{self, Write},
    sync::Arc,
    time::Duration,
};

use bytes::Bytes;
use praxis_filter::{
    BodyAccess, BodyMode, FilterAction, FilterError, HttpFilter, HttpFilterContext,
};
use serde::Deserialize;
use serde_json::{Value, json};
use wanaku_types::{
    audit::{AuditCategory, AuditDecision, AuditEvent, AuditHandle, AuditStore},
    governance::{EnforcementMode, FailureBehavior, GovernanceConfig, GovernancePosture},
    integrity::{
        ContentInspector, ContentKind, Finding, Inspection, InspectionFailure, IntegrityAction,
    },
    registry::DEFAULT_NAMESPACE,
};

const FILTER: &str = "wanaku_mcp_integrity";
const MAX_SCAN_BYTES: usize = 1_048_576;
const MAX_SCANNER_REPLY: usize = 4096;
const REDACTED: &str = "[REDACTED]";
const EXTERNAL_SCANNER: &str = "external.https.v1";

struct LimitedWriter<W> {
    inner: W,
    remaining: usize,
}

impl<W: Write> Write for LimitedWriter<W> {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        if bytes.len() > self.remaining {
            return Err(io::Error::other("inspection byte limit exceeded"));
        }
        let written = self.inner.write(bytes)?;
        self.remaining -= written;
        Ok(written)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

#[derive(Default, Deserialize)]
#[serde(default, deny_unknown_fields)]
struct Config {
    rules: Vec<Rule>,
    external: Option<ExternalConfig>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Rule {
    id: String,
    kind: ContentKind,
    action: IntegrityAction,
    #[serde(default)]
    pointer: String,
    check: Check,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields, rename_all = "snake_case", tag = "type")]
enum Check {
    Size {
        max_bytes: usize,
    },
    Type {
        expected: JsonType,
    },
    Field,
    SecretPattern {
        #[serde(deserialize_with = "deserialize_pattern")]
        pattern: regex::Regex,
    },
}

fn deserialize_pattern<'de, D: serde::Deserializer<'de>>(de: D) -> Result<regex::Regex, D::Error> {
    let pattern = String::deserialize(de)?;
    regex::Regex::new(&pattern).map_err(serde::de::Error::custom)
}

#[derive(Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
enum JsonType {
    Null,
    Boolean,
    Number,
    String,
    Array,
    Object,
}

impl From<&Value> for JsonType {
    fn from(value: &Value) -> Self {
        match value {
            Value::Null => Self::Null,
            Value::Bool(_) => Self::Boolean,
            Value::Number(_) => Self::Number,
            Value::String(_) => Self::String,
            Value::Array(_) => Self::Array,
            Value::Object(_) => Self::Object,
        }
    }
}

impl Rule {
    fn finding(&self, content: &Value) -> Option<Finding> {
        let value = content.pointer(&self.pointer)?;
        let check = match &self.check {
            Check::Size { max_bytes }
                if serde_json::to_writer(
                    LimitedWriter {
                        inner: io::sink(),
                        remaining: *max_bytes,
                    },
                    value,
                )
                .is_err() =>
            {
                "size_exceeded"
            }
            Check::Type { expected } if JsonType::from(value) != *expected => "unexpected_type",
            Check::Field => "field_present",
            Check::SecretPattern { pattern } if has_pattern(value, pattern) => "secret_pattern",
            _ => return None,
        };
        Some(Finding {
            rule: self.id.clone(),
            scanner: "wanaku.local.v1",
            check,
            action: self.action,
        })
    }

    fn redact(&self, content: &mut Value) {
        if let Some(value) = content.pointer_mut(&self.pointer) {
            match &self.check {
                Check::Field => *value = json!(REDACTED),
                Check::SecretPattern { pattern } => redact_pattern(value, pattern),
                _ => {}
            }
        }
    }
}

fn has_pattern(value: &Value, pattern: &regex::Regex) -> bool {
    match value {
        Value::String(text) => pattern.is_match(text),
        Value::Array(items) => items.iter().any(|item| has_pattern(item, pattern)),
        Value::Object(fields) => fields.values().any(|item| has_pattern(item, pattern)),
        _ => false,
    }
}

fn redact_pattern(value: &mut Value, pattern: &regex::Regex) {
    match value {
        Value::String(text) => {
            if pattern.is_match(text) {
                *text = REDACTED.to_owned();
            }
        }
        Value::Array(items) => items
            .iter_mut()
            .for_each(|item| redact_pattern(item, pattern)),
        Value::Object(fields) => fields
            .values_mut()
            .for_each(|item| redact_pattern(item, pattern)),
        _ => {}
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct ExternalConfig {
    id: String,
    url: String,
    kinds: Vec<ContentKind>,
    action: IntegrityAction,
}

struct ExternalScanner {
    config: ExternalConfig,
    client: reqwest::Client,
    url: reqwest::Url,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct ExternalReply {
    matched: bool,
}

impl ExternalScanner {
    fn new(config: ExternalConfig) -> Result<Self, FilterError> {
        let url = reqwest::Url::parse(&config.url)?;
        if url.scheme() != "https"
            || !url.username().is_empty()
            || url.password().is_some()
            || config.kinds.is_empty()
            || config.action == IntegrityAction::Redact
        {
            return Err("integrity external scanner requires an HTTPS URL without credentials, explicit kinds, and a non-redact action".into());
        }
        let client = reqwest::Client::builder()
            .timeout(Duration::from_millis(500))
            .redirect(reqwest::redirect::Policy::none())
            .no_proxy()
            .build()?;
        Ok(Self {
            config,
            client,
            url,
        })
    }

    async fn scan(
        &self,
        kind: ContentKind,
        content: &Value,
    ) -> Result<Option<Finding>, &'static str> {
        #[derive(serde::Serialize)]
        struct Request<'a> {
            kind: ContentKind,
            content: &'a Value,
        }

        let mut body = Vec::new();
        serde_json::to_writer(
            LimitedWriter {
                inner: &mut body,
                remaining: MAX_SCAN_BYTES,
            },
            &Request { kind, content },
        )
        .map_err(|_| "scanner_request_size_limit")?;
        let mut response = self
            .client
            .post(self.url.clone())
            .header(http::header::CONTENT_TYPE, "application/json")
            .body(body)
            .send()
            .await
            .map_err(|_| "scanner_unavailable")?;
        if !response.status().is_success() {
            return Err("scanner_unavailable");
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response.chunk().await.map_err(|_| "scanner_unavailable")? {
            if chunk.len() > MAX_SCANNER_REPLY - bytes.len() {
                return Err("scanner_reply_size_limit");
            }
            bytes.extend_from_slice(&chunk);
        }
        let reply: ExternalReply =
            serde_json::from_slice(&bytes).map_err(|_| "invalid_scanner_reply")?;
        Ok(reply.matched.then(|| Finding {
            rule: self.config.id.clone(),
            scanner: EXTERNAL_SCANNER,
            check: "external_match",
            action: self.config.action,
        }))
    }
}

struct Scanner {
    rules: Vec<Rule>,
    external: Option<ExternalScanner>,
}

impl Scanner {
    async fn inspect(
        &self,
        kind: ContentKind,
        content: &Value,
        external_allowed: bool,
    ) -> Inspection {
        let mut inspection = Inspection::default();
        // An MCP tool-level error has a result envelope. Apply both sets of
        // local rules before any redaction changes the evidence.
        let tool_error = kind == ContentKind::UpstreamError
            && content.get("isError").and_then(Value::as_bool) == Some(true);
        for rule in self.rules.iter().filter(|rule| {
            rule.kind == kind || (tool_error && rule.kind == ContentKind::ToolResult)
        }) {
            if let Some(finding) = rule.finding(content) {
                if finding.action == IntegrityAction::Redact {
                    rule.redact(
                        inspection
                            .replacement
                            .get_or_insert_with(|| content.clone()),
                    );
                }
                inspection.findings.push(finding);
            }
        }
        if let Some(external) = self.external.as_ref().filter(|scanner| {
            external_allowed
                && scanner.config.kinds.contains(&kind)
                && finding_action(&inspection) < IntegrityAction::RequireApproval
        }) {
            let candidate = inspection.replacement.as_ref().unwrap_or(content);
            match external.scan(kind, candidate).await {
                Ok(finding) => inspection.findings.extend(finding),
                Err(reason) => {
                    inspection.failure = Some(InspectionFailure {
                        scanner: EXTERNAL_SCANNER,
                        scanner_id: Some(external.config.id.clone()),
                        reason,
                    });
                }
            }
        }
        inspection
    }
}

#[async_trait::async_trait]
impl ContentInspector for Scanner {
    async fn inspect_input(&self, arguments: &Value) -> Inspection {
        self.inspect(ContentKind::ToolArguments, arguments, true)
            .await
    }

    async fn inspect_outcome(&self, kind: ContentKind, content: &Value) -> Inspection {
        self.inspect(kind, content, true).await
    }
}

pub struct IntegrityFilter(Arc<Scanner>);

impl IntegrityFilter {
    pub fn from_config(config: &serde_yaml::Value) -> Result<Box<dyn HttpFilter>, FilterError> {
        let config: Config = praxis_filter::parse_filter_config(FILTER, config)?;
        for rule in &config.rules {
            if (!rule.pointer.is_empty() && !rule.pointer.starts_with('/'))
                || (matches!(rule.check, Check::Field) && rule.pointer.is_empty())
                || (rule.action == IntegrityAction::Redact
                    && matches!(rule.check, Check::Size { .. } | Check::Type { .. }))
            {
                return Err("invalid integrity rule pointer or redaction check".into());
            }
        }
        Ok(Box::new(Self(Arc::new(Scanner {
            rules: config.rules,
            external: config.external.map(ExternalScanner::new).transpose()?,
        }))))
    }
}

#[async_trait::async_trait]
impl HttpFilter for IntegrityFilter {
    fn name(&self) -> &'static str {
        FILTER
    }

    fn request_body_access(&self) -> BodyAccess {
        if self.0.rules.is_empty() && self.0.external.is_none() {
            BodyAccess::None
        } else {
            BodyAccess::ReadWrite
        }
    }

    fn request_body_mode(&self) -> BodyMode {
        BodyMode::StreamBuffer {
            max_bytes: Some(MAX_SCAN_BYTES),
        }
    }

    async fn on_request(
        &self,
        _ctx: &mut HttpFilterContext<'_>,
    ) -> Result<FilterAction, FilterError> {
        Ok(FilterAction::Continue)
    }

    async fn on_request_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
        end_of_stream: bool,
    ) -> Result<FilterAction, FilterError> {
        if !end_of_stream || (self.0.rules.is_empty() && self.0.external.is_none()) {
            return Ok(FilterAction::Continue);
        }
        let method = ctx.get_metadata(crate::MCP_METHOD_KEY);
        if !matches!(
            method,
            Some(crate::TOOLS_CALL | crate::RESOURCES_READ | crate::PROMPTS_GET)
        ) {
            return Ok(FilterAction::Continue);
        }
        let is_tool_call = method == Some(crate::TOOLS_CALL);
        ctx.extensions.insert(self.0.clone());
        if !is_tool_call {
            return Ok(FilterAction::Continue);
        }
        let mut request = body
            .as_ref()
            .and_then(|bytes| serde_json::from_slice::<Value>(bytes).ok());
        let empty = json!({});
        let arguments = request
            .as_ref()
            .map(|request| request.pointer("/params/arguments").unwrap_or(&empty));
        match inspect(ctx, ContentKind::ToolArguments, arguments, true).await {
            Applied::Reject(action) => {
                let id =
                    crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
                return Ok(reject(&id, action));
            }
            Applied::Replace(replacement) => {
                if let Some(request) = request.as_mut()
                    && let Some(arguments) = request.pointer_mut("/params/arguments")
                {
                    *arguments = replacement;
                    *body = Some(Bytes::from(request.to_string()));
                }
            }
            Applied::Pass => {}
        }
        Ok(FilterAction::Continue)
    }
}

fn finding_action(inspection: &Inspection) -> IntegrityAction {
    inspection
        .findings
        .iter()
        .map(|finding| finding.action)
        .max()
        .unwrap_or(IntegrityAction::Allow)
}

enum Applied {
    Pass,
    Replace(Value),
    Reject(IntegrityAction),
}

async fn inspect(
    ctx: &HttpFilterContext<'_>,
    kind: ContentKind,
    content: Option<&Value>,
    external_allowed: bool,
) -> Applied {
    let Some(scanner) = ctx.extensions.get::<Arc<Scanner>>() else {
        return Applied::Pass;
    };
    let governance = ctx.extensions.get::<GovernanceConfig>();
    let concrete = ctx
        .get_metadata(crate::NAMESPACE_METADATA_KEY)
        .unwrap_or(DEFAULT_NAMESPACE);
    let scopes: Vec<_> = std::iter::once(concrete)
        .chain(ctx.get_metadata(wanaku_types::VIRTUAL_NAMESPACE_METADATA_KEY))
        .map(|namespace| {
            (
                namespace,
                governance.map_or_else(GovernancePosture::default, |config| {
                    config.resolve(namespace)
                }),
            )
        })
        .filter(|(_, posture)| posture.mode != EnforcementMode::Disabled)
        .collect();
    if scopes.is_empty() {
        return Applied::Pass;
    }
    let inspection = match (content, kind, external_allowed) {
        (None, _, _) => Inspection {
            failure: Some(InspectionFailure {
                scanner: FILTER,
                scanner_id: None,
                reason: "invalid_inspection_content",
            }),
            ..Inspection::default()
        },
        (Some(content), _, false) => scanner.inspect(kind, content, false).await,
        (Some(content), ContentKind::ToolArguments, true) => scanner.inspect_input(content).await,
        (Some(content), _, true) => scanner.inspect_outcome(kind, content).await,
    };
    let mut effective = IntegrityAction::Allow;
    for (namespace, posture) in scopes {
        let action = effective_action(&posture, &inspection);
        effective = effective.max(action);
        record(ctx, namespace, kind, &inspection, action);
    }
    match effective {
        IntegrityAction::RequireApproval | IntegrityAction::Block => Applied::Reject(effective),
        IntegrityAction::Redact => inspection
            .replacement
            .map_or(Applied::Reject(IntegrityAction::Block), Applied::Replace),
        IntegrityAction::Allow | IntegrityAction::Tag => Applied::Pass,
    }
}

fn effective_action(posture: &GovernancePosture, inspection: &Inspection) -> IntegrityAction {
    match (posture.mode, &inspection.failure, posture.on_failure) {
        (EnforcementMode::Audit | EnforcementMode::Disabled, _, _) => IntegrityAction::Allow,
        (_, Some(_), FailureBehavior::Deny) => IntegrityAction::Block,
        _ => finding_action(inspection),
    }
}

fn record(
    ctx: &HttpFilterContext<'_>,
    namespace: &str,
    kind: ContentKind,
    inspection: &Inspection,
    action: IntegrityAction,
) {
    let Some(store) = ctx.extensions.get::<AuditHandle>() else {
        return;
    };
    let decision = match action {
        IntegrityAction::Block | IntegrityAction::RequireApproval => AuditDecision::Block,
        _ if inspection.failure.is_some() => AuditDecision::Error,
        IntegrityAction::Allow => AuditDecision::Allow,
        _ => AuditDecision::Observed,
    };
    let mut event = AuditEvent::new(
        AuditCategory::Decision,
        decision,
        ctx.get_metadata(crate::MCP_METHOD_KEY).unwrap_or("mcp"),
        "mcp_content_integrity",
        "MCP content inspection completed.",
    );
    event.filter = Some(FILTER.to_owned());
    event.namespace = Some(namespace.to_owned());
    event.request_id = ctx
        .request
        .headers
        .get("x-request-id")
        .and_then(|value| value.to_str().ok())
        .map(str::to_owned);
    if let Some(id) = &event.request_id {
        event.correlation_id.clone_from(id);
    }
    event.attributes.insert("content_kind".into(), json!(kind));
    event
        .attributes
        .insert("findings".into(), json!(inspection.findings));
    event.attributes.insert("action".into(), json!(action));
    if let Some(failure) = &inspection.failure {
        event
            .attributes
            .insert("scanner_failure".into(), json!(failure));
    }
    store.record(event);
}

fn reject(id: &Value, action: IntegrityAction) -> FilterAction {
    let (code, message) = if action == IntegrityAction::RequireApproval {
        (-32004, "MCP content requires approval.")
    } else {
        (-32003, "MCP content was blocked by integrity policy.")
    };
    crate::response::json_rpc_host_error(
        id,
        code,
        wanaku_types::wasm::HostErrorKind::Denied,
        message,
    )
}

pub(crate) async fn inspect_mapped_arguments(
    ctx: &HttpFilterContext<'_>,
    arguments: &mut std::collections::HashMap<String, Value>,
    id: &Value,
) -> Result<(), FilterAction> {
    if ctx.extensions.get::<Arc<Scanner>>().is_none() {
        return Ok(());
    }
    let content = json!(arguments);
    match inspect(ctx, ContentKind::ToolArguments, Some(&content), false).await {
        Applied::Pass => {}
        Applied::Reject(action) => return Err(reject(id, action)),
        Applied::Replace(value) => {
            *arguments =
                serde_json::from_value(value).map_err(|_| reject(id, IntegrityAction::Block))?;
        }
    }
    Ok(())
}

pub(crate) async fn inspect_response(
    ctx: &HttpFilterContext<'_>,
    kind: ContentKind,
    action: FilterAction,
) -> FilterAction {
    if ctx.extensions.get::<Arc<Scanner>>().is_none() {
        return action;
    }
    let FilterAction::Reject(mut response) = action else {
        return action;
    };
    let Some(mut envelope) = response
        .body
        .as_ref()
        .and_then(|bytes| serde_json::from_slice::<Value>(bytes).ok())
    else {
        if let Applied::Reject(action) = inspect(ctx, kind, None, true).await {
            let id =
                crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
            return reject(&id, action);
        }
        return FilterAction::Reject(response);
    };
    let (kind, key) = if envelope.get("error").is_some() {
        (ContentKind::UpstreamError, "error")
    } else if kind == ContentKind::ToolResult
        && envelope.pointer("/result/isError").and_then(Value::as_bool) == Some(true)
    {
        (ContentKind::UpstreamError, "result")
    } else {
        (kind, "result")
    };
    match inspect(ctx, kind, envelope.get(key), true).await {
        Applied::Pass => {}
        Applied::Replace(replacement) => {
            envelope[key] = replacement;
            response.body = Some(Bytes::from(envelope.to_string()));
        }
        Applied::Reject(action) => {
            return reject(envelope.get("id").unwrap_or(&Value::Null), action);
        }
    }
    FilterAction::Reject(response)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::invoker::testing::{
        RecordingInvoker, filter_context, registry, request, response_json,
    };
    use wanaku_infra::audit::InMemoryAuditStore;
    use wanaku_types::audit::AuditQuery;

    fn filter(config: &str) -> Box<dyn HttpFilter> {
        IntegrityFilter::from_config(&serde_yaml::from_str(config).unwrap()).unwrap()
    }

    #[tokio::test]
    async fn rewrites_only_matching_argument_strings_and_audits_without_content() {
        let filter = filter(
            r"
rules:
  - id: service_token
    kind: tool_arguments
    check: {type: secret_pattern, pattern: '\bsvc_[A-Z0-9]{8}\b'}
    action: redact
  - id: note_present
    kind: tool_arguments
    pointer: /note
    check: {type: field}
    action: tag
",
        );
        let req = request(&[("x-request-id", "test-request")]);
        let invoker = RecordingInvoker::succeeding(json!({}));
        let mut ctx = filter_context(&req, crate::TOOLS_CALL, &registry(None, None), &invoker);
        let audit = Arc::new(InMemoryAuditStore::new(10));
        ctx.extensions.insert(AuditHandle::new(audit.clone()));
        let mut body = Some(Bytes::from_static(
            br#"{"jsonrpc":"2.0","id":7,"params":{"name":"echo","arguments":{"nested":["key svc_Z1234567", "svc_EXAMPLE", "a regular note"],"note":"unchanged"}}}"#,
        ));
        assert!(matches!(
            filter
                .on_request_body(&mut ctx, &mut body, true)
                .await
                .unwrap(),
            FilterAction::Continue
        ));
        let result: Value = serde_json::from_slice(&body.unwrap()).unwrap();
        assert_eq!(result["id"], 7);
        assert_eq!(result["params"]["name"], "echo");
        assert_eq!(
            result["params"]["arguments"]["nested"],
            json!(["[REDACTED]", "svc_EXAMPLE", "a regular note",])
        );
        assert_eq!(result["params"]["arguments"]["note"], "unchanged");

        let events = audit
            .query(&AuditQuery {
                limit: 10,
                ..AuditQuery::default()
            })
            .events;
        assert_eq!(events.len(), 1);
        assert_eq!(events[0].correlation_id, "test-request");
        assert_eq!(events[0].attributes["action"], "redact");
        assert_eq!(
            events[0].attributes["findings"][0]["scanner"],
            "wanaku.local.v1"
        );
        assert_eq!(events[0].attributes["findings"][0]["rule"], "service_token");
        assert_eq!(events[0].attributes["findings"][1]["action"], "tag");
        assert!(
            !serde_json::to_string(&events)
                .unwrap()
                .contains("svc_Z1234567")
        );
    }

    #[tokio::test]
    async fn blocks_wrong_type_before_dispatch_and_preserves_id() {
        let filter = filter(
            r"
rules:
  - id: amount_type
    kind: tool_arguments
    pointer: /amount
    check: {type: type, expected: number}
    action: block
",
        );
        let req = request(&[]);
        let invoker = RecordingInvoker::succeeding(json!({}));
        let mut ctx = filter_context(&req, crate::TOOLS_CALL, &registry(None, None), &invoker);
        ctx.set_metadata(crate::MCP_ID_KEY, "7");
        let mut body = Some(Bytes::from_static(
            br#"{"id":7,"params":{"arguments":{"amount":"large"}}}"#,
        ));
        let response = response_json(
            filter
                .on_request_body(&mut ctx, &mut body, true)
                .await
                .unwrap(),
        );
        assert_eq!(response["error"]["code"], -32003);
        assert_eq!(response["id"], 7);
        assert!(invoker.calls().is_empty());
    }

    #[tokio::test]
    async fn withholds_outcomes_for_approval_and_redacts_upstream_errors() {
        let filter = filter(
            r"
rules:
  - id: resource_size
    kind: resource_contents
    check: {type: size, max_bytes: 8}
    action: require_approval
  - id: error_text
    kind: upstream_error
    pointer: /message
    check: {type: field}
    action: redact
",
        );
        let req = request(&[]);
        let invoker = RecordingInvoker::succeeding(json!({}));
        let mut ctx = filter_context(&req, crate::RESOURCES_READ, &registry(None, None), &invoker);
        assert!(matches!(
            filter
                .on_request_body(&mut ctx, &mut None, true)
                .await
                .unwrap(),
            FilterAction::Continue
        ));

        let success = FilterAction::Reject(crate::response::json_response(Bytes::from_static(
            br#"{"jsonrpc":"2.0","id":7,"result":{"contents":[{"text":"private"}]}}"#,
        )));
        let withheld =
            response_json(inspect_response(&ctx, ContentKind::ResourceContents, success).await);
        assert_eq!(withheld["error"]["code"], -32004);
        assert!(withheld.get("result").is_none());

        let error = crate::response::json_rpc_error(&json!(7), -32603, "private");
        let redacted =
            response_json(inspect_response(&ctx, ContentKind::ResourceContents, error).await);
        assert_eq!(redacted["error"]["message"], REDACTED);
        assert_eq!(redacted["error"]["code"], -32603);
    }

    #[tokio::test]
    async fn external_request_limit_flows_through_effective_failure_posture() {
        let filter = filter(
            r"
external:
  id: remote_test
  url: https://scanner.invalid/inspect
  kinds: [resource_contents]
  action: block
",
        );
        let req = request(&[]);
        let invoker = RecordingInvoker::succeeding(json!({}));
        let mut ctx = filter_context(&req, crate::RESOURCES_READ, &registry(None, None), &invoker);
        assert!(matches!(
            filter
                .on_request_body(&mut ctx, &mut None, true)
                .await
                .unwrap(),
            FilterAction::Continue
        ));
        let audit = Arc::new(InMemoryAuditStore::new(10));
        ctx.extensions.insert(AuditHandle::new(audit.clone()));
        let large = Value::String("a".repeat(MAX_SCAN_BYTES));

        assert!(matches!(
            inspect(&ctx, ContentKind::ResourceContents, Some(&large), true).await,
            Applied::Reject(IntegrityAction::Block)
        ));

        for posture in [
            GovernancePosture {
                on_failure: FailureBehavior::Allow,
                ..GovernancePosture::default()
            },
            GovernancePosture {
                mode: EnforcementMode::Audit,
                ..GovernancePosture::default()
            },
        ] {
            ctx.extensions.insert(GovernanceConfig {
                default: posture,
                ..GovernanceConfig::default()
            });
            assert!(matches!(
                inspect(&ctx, ContentKind::ResourceContents, Some(&large), true).await,
                Applied::Pass
            ));
        }
        let events = audit
            .query(&AuditQuery {
                limit: 10,
                ..AuditQuery::default()
            })
            .events;
        assert_eq!(events.len(), 3);
        for event in events {
            assert_eq!(
                event.attributes["scanner_failure"]["reason"],
                "scanner_request_size_limit"
            );
            assert_eq!(
                event.attributes["scanner_failure"]["scanner_id"],
                "remote_test"
            );
        }
    }
}
