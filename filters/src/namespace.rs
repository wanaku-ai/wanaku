use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use serde_json::Value;
use wanaku_infra::registry::RuntimeRegistry;
use wanaku_types::registry::{NamespaceEntry, NamespaceRegistry, ToolRegistry};

pub use wanaku_types::metadata::NAMESPACE_METADATA_KEY;
use wanaku_types::metadata::VIRTUAL_NAMESPACE_METADATA_KEY;

crate::body_filter_boilerplate!(NamespaceFilter, "wanaku_namespace", ReadWrite);

fn extract_namespace(path: &str) -> Option<&str> {
    let trimmed = path.strip_prefix('/').unwrap_or(path).trim_end_matches('/');

    if let Some(ns) = trimmed.strip_suffix("/mcp")
        && valid_namespace_path(ns)
    {
        return Some(ns);
    }

    None
}

fn valid_namespace_path(namespace: &str) -> bool {
    let name = namespace.strip_prefix("virtual-ns/").unwrap_or(namespace);
    wanaku_types::registry::validate_namespace_name(name).is_ok()
}

/// Resolve a qualified virtual request before governance and credential checks.
/// The request body keeps the original concrete upstream identifier thereafter.
fn resolve_virtual_request(
    registry: &RuntimeRegistry,
    group: &str,
    method: &str,
    request: &mut Value,
) -> Result<Option<(String, String)>, &'static str> {
    registry
        .get_namespace(group)
        .filter(NamespaceEntry::is_virtual)
        .ok_or("virtual namespace not found")?;
    let (field, prefix) = match method {
        crate::TOOLS_CALL | crate::PROMPTS_GET => ("name", ""),
        crate::RESOURCES_READ => ("uri", "wanaku://"),
        _ => return Ok(None),
    };
    let identifier = request
        .get("params")
        .and_then(|params| params.get(field))
        .and_then(Value::as_str)
        .ok_or("virtual namespace requests require a qualified name or URI")?;
    let (member, original) = identifier
        .strip_prefix(prefix)
        .and_then(|identifier| identifier.split_once('/'))
        .filter(|(_, original)| !original.is_empty())
        .ok_or("virtual namespace requests require a qualified name or URI")?;
    if !registry.resolve_virtual_member(group, member) {
        return Err("namespace is not a concrete member of this virtual namespace");
    }
    let target = (member.to_owned(), original.to_owned());
    request["params"][field] = Value::String(target.1.clone());
    Ok(Some(target))
}

impl NamespaceFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let Some(namespace) = extract_namespace(ctx.request.uri.path()).map(str::to_owned) else {
            return Ok(invalid_namespace_path(ctx));
        };
        if let Some(group) = namespace.strip_prefix("virtual-ns/") {
            return resolve_virtual_context(ctx, body, group);
        }
        let is_virtual = ctx
            .extensions
            .get::<RuntimeRegistry>()
            .and_then(|registry| registry.get_namespace(&namespace))
            .is_some_and(|entry| entry.is_virtual());
        if is_virtual {
            return Ok(namespace_error(
                ctx,
                "use /virtual-ns/{namespace}/mcp for virtual namespaces",
            ));
        }
        ctx.set_metadata(NAMESPACE_METADATA_KEY, namespace);
        pin_tool(ctx);
        Ok(FilterAction::Continue)
    }
}

fn invalid_namespace_path(ctx: &HttpFilterContext<'_>) -> FilterAction {
    if ctx.get_metadata(crate::MCP_METHOD_KEY).is_none() {
        return FilterAction::Continue;
    }
    let id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
    crate::response::json_rpc_error(
        &id,
        crate::response::JSONRPC_INVALID_REQUEST,
        "invalid MCP endpoint path: use /{namespace}/mcp or /virtual-ns/{namespace}/mcp",
    )
}

fn namespace_error(ctx: &HttpFilterContext<'_>, message: &str) -> FilterAction {
    let id = crate::response::json_rpc_id_from_metadata(ctx.get_metadata(crate::MCP_ID_KEY));
    crate::response::json_rpc_error(&id, crate::response::JSONRPC_INVALID_PARAMS, message)
}

fn resolve_virtual_context(
    ctx: &mut HttpFilterContext<'_>,
    body: &mut Option<Bytes>,
    group: &str,
) -> Result<FilterAction, FilterError> {
    let Some(registry) = ctx.extensions.get::<RuntimeRegistry>() else {
        return Ok(namespace_error(ctx, "registry unavailable"));
    };
    let method = ctx.get_metadata(crate::MCP_METHOD_KEY).unwrap_or_default();
    let mut request = match body.as_ref() {
        Some(bytes) => serde_json::from_slice(bytes).unwrap_or(Value::Null),
        None => Value::Null,
    };
    let resolution = match resolve_virtual_request(registry, group, method, &mut request) {
        Ok(resolution) => resolution,
        Err(message) => return Ok(namespace_error(ctx, message)),
    };
    ctx.set_metadata(VIRTUAL_NAMESPACE_METADATA_KEY, group);
    match resolution {
        Some((namespace, identifier)) => {
            ctx.set_metadata(NAMESPACE_METADATA_KEY, namespace);
            ctx.set_metadata(crate::MCP_NAME_KEY, identifier);
            *body = Some(Bytes::from(request.to_string()));
        }
        None => ctx.set_metadata(NAMESPACE_METADATA_KEY, format!("virtual-ns/{group}")),
    }
    pin_tool(ctx);
    Ok(FilterAction::Continue)
}

fn pin_tool(ctx: &mut HttpFilterContext<'_>) {
    if ctx.get_metadata(crate::MCP_METHOD_KEY) != Some(crate::TOOLS_CALL) {
        return;
    }
    ctx.extensions
        .insert(wanaku_types::wasm::ToolResolutionComplete);
    ctx.extensions.remove::<wanaku_types::wasm::ResolvedTool>();
    let tool = ctx
        .extensions
        .get::<RuntimeRegistry>()
        .and_then(|registry| {
            registry.get_tool_in_namespace(
                ctx.get_metadata(NAMESPACE_METADATA_KEY)?,
                ctx.get_metadata(crate::MCP_NAME_KEY)?,
            )
        });
    if let Some(tool) = tool {
        ctx.extensions
            .insert(wanaku_types::wasm::ResolvedTool(tool));
    }
}

/// Qualify only MCP resource references. User text and structured data stay intact.
pub(crate) fn qualify_resource_result(result: &mut Value, namespace: &str, method: &str) {
    match method {
        crate::TOOLS_CALL => {
            if let Some(contents) = result.get_mut("content").and_then(Value::as_array_mut) {
                for content in contents {
                    qualify_resource_content(content, namespace);
                }
            }
        }
        crate::PROMPTS_GET => {
            if let Some(messages) = result.get_mut("messages").and_then(Value::as_array_mut) {
                for message in messages {
                    if let Some(content) = message.get_mut("content") {
                        qualify_resource_content(content, namespace);
                    }
                }
            }
        }
        crate::RESOURCES_READ => {
            if let Some(contents) = result.get_mut("contents").and_then(Value::as_array_mut) {
                for content in contents {
                    qualify_uri(content, namespace);
                }
            }
        }
        _ => {}
    }
}

fn qualify_resource_content(content: &mut Value, namespace: &str) {
    match content.get("type").and_then(Value::as_str) {
        Some("resource_link") => {
            if content
                .get("uri")
                .and_then(Value::as_str)
                .is_some_and(|uri| uri.starts_with("wanaku://"))
            {
                return;
            }
            qualify_uri(content, namespace);
            if let Some(name) = content.get("name").and_then(Value::as_str) {
                content["name"] = Value::String(format!("{namespace}/{name}"));
            }
        }
        Some("resource") => {
            if let Some(resource) = content.get_mut("resource") {
                qualify_uri(resource, namespace);
            }
        }
        _ => {}
    }
}

fn qualify_uri(content: &mut Value, namespace: &str) {
    if let Some(uri) = content.get("uri").and_then(Value::as_str) {
        if uri.starts_with("wanaku://") {
            return;
        }
        content["uri"] = Value::String(format!("wanaku://{namespace}/{uri}"));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bare_mcp_is_none() {
        assert_eq!(extract_namespace("/mcp"), None);
    }

    #[test]
    fn default_namespace_explicit() {
        assert_eq!(extract_namespace("/default/mcp"), Some("default"));
    }

    #[test]
    fn namespace_from_path() {
        assert_eq!(extract_namespace("/finance/mcp"), Some("finance"));
    }

    #[test]
    fn another_namespace() {
        assert_eq!(extract_namespace("/engineering/mcp"), Some("engineering"));
    }

    #[test]
    fn nested_path_is_none() {
        assert_eq!(extract_namespace("/a/b/mcp"), None);
    }

    #[test]
    fn no_mcp_suffix_is_none() {
        assert_eq!(extract_namespace("/finance/other"), None);
    }

    #[test]
    fn empty_path_is_none() {
        assert_eq!(extract_namespace("/"), None);
    }

    #[test]
    fn mcp_prefix_namespace_is_none() {
        assert_eq!(extract_namespace("/mcp/test-ns"), None);
    }

    #[test]
    fn mcp_prefix_default_is_none() {
        assert_eq!(extract_namespace("/mcp/default"), None);
    }

    #[test]
    fn mcp_prefix_nested_is_none() {
        assert_eq!(extract_namespace("/mcp/a/b"), None);
    }

    #[test]
    fn trailing_slash_namespace_mcp() {
        assert_eq!(extract_namespace("/test-ns2/mcp/"), Some("test-ns2"));
    }

    #[test]
    fn bare_namespace_is_none() {
        assert_eq!(extract_namespace("/finance"), None);
    }

    #[test]
    fn bare_namespace_with_trailing_slash_is_none() {
        assert_eq!(extract_namespace("/finance/"), None);
    }

    #[test]
    fn virtual_endpoint_is_explicit_and_validated() {
        assert_eq!(
            extract_namespace("/virtual-ns/integration/mcp"),
            Some("virtual-ns/integration")
        );
        assert_eq!(extract_namespace("/virtual-ns/a/b/mcp"), None);
        assert_eq!(extract_namespace("/virtual-ns/Bad/mcp"), None);
    }
}

#[cfg(test)]
mod virtual_tests {
    use praxis_filter::{BodyAccess, HttpFilter};
    use serde_json::json;
    use wanaku_types::registry::{BindingRegistry, PromptRegistry, ResourceRegistry, ToolRegistry};

    use super::*;
    use crate::invoker::testing::{
        FORWARD_ID, RecordingInvoker, filter_context, registry, request, response_json,
    };

    fn virtual_registry() -> RuntimeRegistry {
        let registry = registry(Some("b1"), Some("fake:token"));
        let mut binding = registry.get_binding("b1").unwrap();
        binding.restrictions.namespaces = vec!["default".to_owned()];
        registry.register_binding(binding);
        registry.register_namespace(
            serde_json::from_value(json!({
                "name":"integration", "members":["default"]
            }))
            .unwrap(),
        );
        registry.register_tool(serde_json::from_value(json!({
                "name":"health-check", "description":"health", "inputSchema":{"type":"object"}, "uri":"health-check", "type":"mcp-forward", "forwardId":FORWARD_ID
        })).unwrap());
        registry.register_resource(serde_json::from_value(json!({
            "name":"report", "location":"file:///report/{id}", "type":"mcp-forward", "forwardId":FORWARD_ID,
            "labels":{"wanaku.is_template":"true"}
        })).unwrap());
        registry.register_prompt(
            serde_json::from_value(json!({
                "name":"summary", "type":"mcp-forward", "forwardId":FORWARD_ID
            }))
            .unwrap(),
        );
        registry
    }

    #[tokio::test]
    async fn qualified_actions_dispatch_original_identifiers_with_concrete_credentials() {
        let registry = virtual_registry();
        let namespace = NamespaceFilter {
            max_body_bytes: 4096,
        };
        assert_eq!(namespace.request_body_access(), BodyAccess::ReadWrite);
        let config = serde_yaml::Value::Null;
        let cases = [
            (
                crate::TOOLS_CALL,
                "name",
                "default/health-check",
                "health-check",
                crate::ToolCallFilter::from_config(&config).unwrap(),
            ),
            (
                crate::RESOURCES_READ,
                "uri",
                "wanaku://default/file:///report/42",
                "file:///report/42",
                crate::ResourceReadFilter::from_config(&config).unwrap(),
            ),
            (
                crate::PROMPTS_GET,
                "name",
                "default/summary",
                "summary",
                crate::PromptGetFilter::from_config(&config).unwrap(),
            ),
        ];
        for (method, field, qualified, original, terminal) in cases {
            let invoker = RecordingInvoker::succeeding(json!({
                "content":[
                    {"type":"resource_link","name":"report","uri":"file:///report/42"},
                    {"type":"resource","resource":{"uri":"file:///report/42","text":"report"}},
                    {"type":"text","text":"file:///report/42"}
                ],
                "messages":[{"role":"user","content":{"type":"resource","resource":{"uri":"file:///report/42","text":"report"}}}],
                "structuredContent":{"uri":"file:///report/42"},
                "uri":"file:///report/42", "text":"report"
            }));
            let mut req = request(&[]);
            req.uri = "/virtual-ns/integration/mcp".parse().unwrap();
            let mut ctx = filter_context(&req, method, &registry, &invoker);
            let mut value =
                json!({"jsonrpc":"2.0", "id":7, "method":method,"params":{"arguments":{}}});
            value["params"][field] = json!(qualified);
            let mut body = Some(Bytes::from(value.to_string()));
            assert!(matches!(
                namespace
                    .on_request_body(&mut ctx, &mut body, true)
                    .await
                    .unwrap(),
                FilterAction::Continue
            ));
            assert_eq!(ctx.get_metadata(NAMESPACE_METADATA_KEY), Some("default"));
            assert_eq!(
                ctx.get_metadata(VIRTUAL_NAMESPACE_METADATA_KEY),
                Some("integration")
            );
            let action = terminal
                .on_request_body(&mut ctx, &mut body, true)
                .await
                .unwrap();
            let response = response_json(action);
            assert!(response.get("error").is_none(), "{method}: {response}");
            if method == crate::RESOURCES_READ {
                assert_eq!(response["result"]["contents"][0]["uri"], qualified);
            }
            if method == crate::TOOLS_CALL {
                assert_eq!(
                    response["result"]["content"][0]["uri"],
                    "wanaku://default/file:///report/42"
                );
                assert_eq!(response["result"]["content"][0]["name"], "default/report");
                assert_eq!(
                    response["result"]["content"][1]["resource"]["uri"],
                    "wanaku://default/file:///report/42"
                );
                assert_eq!(
                    response["result"]["content"][2]["text"],
                    "file:///report/42"
                );
                assert_eq!(
                    response["result"]["structuredContent"]["uri"],
                    "file:///report/42"
                );
            }
            if method == crate::PROMPTS_GET {
                assert_eq!(
                    response["result"]["messages"][0]["content"]["resource"]["uri"],
                    "wanaku://default/file:///report/42"
                );
            }
            let calls = invoker.calls();
            assert_eq!(calls.len(), 1, "{method}: {response}");
            assert_eq!(calls[0].target, original);
            assert_eq!(
                calls[0].headers.get(&http::header::AUTHORIZATION).unwrap(),
                "Bearer s3cr3t"
            );
        }
    }

    #[tokio::test]
    async fn virtual_boundary_rejects_unqualified_nonmember_and_wrong_endpoint() {
        let registry = virtual_registry();
        let filter = NamespaceFilter {
            max_body_bytes: 4096,
        };
        for (path, name) in [
            ("/virtual-ns/integration/mcp", "health-check"),
            ("/virtual-ns/integration/mcp", "other/health-check"),
            ("/virtual-ns/missing/mcp", "default/health-check"),
            ("/virtual-ns/default/mcp", "default/health-check"),
            ("/integration/mcp", "default/health-check"),
        ] {
            let invoker = RecordingInvoker::succeeding(json!({}));
            let mut req = request(&[]);
            req.uri = path.parse().unwrap();
            let mut ctx = filter_context(&req, crate::TOOLS_CALL, &registry, &invoker);
            let mut body = Some(Bytes::from(
                json!({"jsonrpc":"2.0", "id":7,"method":crate::TOOLS_CALL,"params":{"name":name}})
                    .to_string(),
            ));
            let response = response_json(
                filter
                    .on_request_body(&mut ctx, &mut body, true)
                    .await
                    .unwrap(),
            );
            assert_eq!(response["error"]["code"], -32602, "{path}: {response}");
            assert!(invoker.calls().is_empty());
        }
    }
}
