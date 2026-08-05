use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use tracing::{trace, warn};
use wanaku_praxis_apis::grpc::GrpcPool;
use wanaku_praxis_apis::registry::{InMemoryRegistry, ResourceRegistry, ServiceRegistry};

crate::body_filter_boilerplate!(ResourceReadFilter, "wanaku_resource_read");

struct ParsedBody {
    id: serde_json::Value,
    uri: Option<String>,
}

fn parse_body(body: &Option<Bytes>) -> ParsedBody {
    let Some(body_bytes) = body else {
        return ParsedBody {
            id: serde_json::Value::Null,
            uri: None,
        };
    };

    let Ok(parsed) = serde_json::from_slice::<serde_json::Value>(body_bytes) else {
        return ParsedBody {
            id: serde_json::Value::Null,
            uri: None,
        };
    };

    let id = parsed
        .get("id")
        .cloned()
        .unwrap_or(serde_json::Value::Null);

    let uri = parsed
        .get("params")
        .and_then(|p| p.get("uri"))
        .and_then(|u| u.as_str())
        .map(str::to_owned);

    ParsedBody { id, uri }
}

impl ResourceReadFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let method = match ctx.get_metadata(crate::MCP_METHOD_KEY) {
            Some(m) => m,
            None => return Ok(FilterAction::Continue),
        };

        if method != "resources/read" {
            return Ok(FilterAction::Continue);
        }

        let parsed = parse_body(body);

        let resource_uri = match &parsed.uri {
            Some(u) => u.clone(),
            None => {
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INVALID_PARAMS, "missing uri in resources/read"));
            }
        };

        let namespace = ctx
            .get_metadata(crate::namespace::NAMESPACE_METADATA_KEY)
            .unwrap_or(wanaku_praxis_apis::registry::DEFAULT_NAMESPACE);

        trace!(uri = %resource_uri, namespace = %namespace, "handling MCP resources/read request");

        let registry = match ctx.extensions.get::<InMemoryRegistry>() {
            Some(r) => r,
            None => {
                tracing::error!("InMemoryRegistry not found in request extensions");
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: registry unavailable"));
            }
        };

        let resource = match find_resource_by_uri(registry, namespace, &resource_uri) {
            Some(r) => r,
            None => {
                warn!(uri = %resource_uri, "resource not found in registry");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INVALID_PARAMS,
                    &format!("resource not found: {resource_uri}"),
                ));
            }
        };

        let service = match registry.resolve_service(&resource.type_, "resource-provider") {
            Ok(s) => s,
            Err(e) => {
                warn!(resource_type = %resource.type_, error = %e, "no service available for resource type");
                return Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("no service available for resource type: {}", resource.type_),
                ));
            }
        };

        let grpc_pool = match ctx.extensions.get::<GrpcPool>() {
            Some(p) => p.clone(),
            None => {
                tracing::error!("GrpcPool not found in request extensions");
                return Ok(crate::response::json_rpc_error(&parsed.id, crate::response::JSONRPC_INTERNAL_ERROR, "internal error: gRPC pool unavailable"));
            }
        };

        match grpc_pool
            .acquire_resource(
                &service.address,
                resource.location.clone(),
                resource.type_.clone(),
                resource.name.clone(),
            )
            .await
        {
            Ok(content) => {
                let mcp_content: Vec<serde_json::Value> = content
                    .iter()
                    .map(|text| {
                        serde_json::json!({
                            "uri": resource_uri,
                            "mimeType": resource.mime_type,
                            "text": text,
                        })
                    })
                    .collect();

                let response = serde_json::json!({
                    "jsonrpc": "2.0",
                    "id": parsed.id,
                    "result": {
                        "contents": mcp_content,
                    }
                });

                let response_body = Bytes::from(response.to_string());
                Ok(FilterAction::Reject(crate::response::json_response(response_body)))
            }
            Err(e) => {
                warn!(uri = %resource_uri, error = %e, "gRPC resource acquire failed");
                Ok(crate::response::json_rpc_error(
                    &parsed.id,
                    crate::response::JSONRPC_INTERNAL_ERROR,
                    &format!("resource read failed: {e}"),
                ))
            }
        }
    }
}

fn find_resource_by_uri(
    registry: &InMemoryRegistry,
    namespace: &str,
    uri: &str,
) -> Option<wanaku_praxis_apis::registry::ResourceEntry> {
    registry
        .list_resources_in_namespace(namespace)
        .into_iter()
        .find(|r| r.location == uri)
}
