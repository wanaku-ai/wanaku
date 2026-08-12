use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext, Rejection};

use crate::IssuerConfig;

wanaku_praxis_filters::body_filter_boilerplate!(WellKnownFilter, "wanaku_well_known");

const WELL_KNOWN_PREFIX: &str = "/.well-known/oauth-protected-resource/";

impl WellKnownFilter {
    async fn handle_body(
        &self,
        ctx: &mut HttpFilterContext<'_>,
        body: &mut Option<Bytes>,
    ) -> Result<FilterAction, FilterError> {
        let path = ctx.request.uri.path();

        if let Some(suffix) = path.strip_prefix(WELL_KNOWN_PREFIX) {
            return self.handle_protected_resource_metadata(ctx, suffix);
        }

        let issuer_config = match ctx.extensions.get::<IssuerConfig>() {
            Some(c) if !c.issuer.is_empty() => c.clone(),
            _ => return Ok(FilterAction::Continue),
        };

        let host = ctx
            .request
            .headers
            .get(http::header::HOST)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("localhost:8081");
        let base = format!("http://{host}");

        match path {
            "/.well-known/openid-configuration"
            | "/.well-known/oauth-authorization-server" => {
                Ok(FilterAction::Reject(self.serve_discovery(&base, &issuer_config)))
            }
            "/authorize" => {
                Ok(FilterAction::Reject(redirect_to(
                    &format!("{}/protocol/openid-connect/auth", issuer_config.issuer),
                    ctx.request.uri.query(),
                )))
            }
            "/token" => {
                let result = proxy_token_endpoint(&issuer_config.issuer, body).await;
                Ok(FilterAction::Reject(result))
            }
            "/register" => {
                Ok(FilterAction::Reject(redirect_to(
                    &format!("{}/clients-registrations/openid-connect", issuer_config.issuer),
                    ctx.request.uri.query(),
                )))
            }
            _ => Ok(FilterAction::Continue),
        }
    }

    fn serve_discovery(&self, base: &str, config: &IssuerConfig) -> Rejection {
        let doc = serde_json::json!({
            "issuer": config.issuer,
            "authorization_endpoint": format!("{base}/authorize"),
            "token_endpoint": format!("{base}/token"),
            "registration_endpoint": format!("{base}/register"),
            "jwks_uri": format!("{}/protocol/openid-connect/certs", config.issuer),
            "response_types_supported": ["code"],
            "grant_types_supported": ["authorization_code", "refresh_token"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"],
            "code_challenge_methods_supported": ["S256"],
            "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post"],
        });

        tracing::debug!("served openid-configuration");
        json_response(200, &doc)
    }

    fn handle_protected_resource_metadata(
        &self,
        ctx: &HttpFilterContext<'_>,
        suffix: &str,
    ) -> Result<FilterAction, FilterError> {
        let namespace = suffix
            .strip_suffix("/mcp")
            .or_else(|| suffix.strip_suffix("/mcp/"))
            .filter(|ns| !ns.is_empty() && !ns.contains('/'))
            .unwrap_or("default");

        let host = ctx
            .request
            .headers
            .get(http::header::HOST)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("localhost:8081");

        let resource = if namespace == "default" {
            format!("http://{host}/mcp")
        } else {
            format!("http://{host}/{namespace}/mcp")
        };

        let issuer_config = ctx.extensions.get::<IssuerConfig>();
        let has_issuer = issuer_config
            .map(|c| !c.issuer.is_empty())
            .unwrap_or(false);

        let host_str = format!("http://{host}");
        let auth_servers: Vec<&str> = if has_issuer {
            vec![host_str.as_str()]
        } else {
            vec![]
        };

        let metadata = serde_json::json!({
            "resource": resource,
            "authorization_servers": auth_servers,
            "bearer_methods_supported": ["header"],
        });

        tracing::debug!(namespace = %namespace, "served protected resource metadata");
        Ok(FilterAction::Reject(json_response(200, &metadata)))
    }
}

async fn proxy_token_endpoint(issuer: &str, body: &Option<Bytes>) -> Rejection {
    let url = format!("{issuer}/protocol/openid-connect/token");

    let req_body = body
        .as_ref()
        .map(|b| b.to_vec())
        .unwrap_or_default();

    let client = match reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(10))
        .build()
    {
        Ok(c) => c,
        Err(e) => {
            tracing::warn!(error = %e, "failed to create HTTP client for token proxy");
            return json_response(503, &serde_json::json!({"error": "token_proxy_error"}));
        }
    };

    match client
        .post(&url)
        .header("content-type", "application/x-www-form-urlencoded")
        .body(req_body)
        .send()
        .await
    {
        Ok(resp) => {
            let status = resp.status().as_u16();
            match resp.bytes().await {
                Ok(resp_body) => {
                    Rejection::status(status)
                        .with_header("content-type", "application/json")
                        .with_header("access-control-allow-origin", "*")
                        .with_body(resp_body)
                }
                Err(e) => {
                    tracing::warn!(error = %e, "failed to read token response");
                    json_response(503, &serde_json::json!({"error": "token_proxy_read_error"}))
                }
            }
        }
        Err(e) => {
            tracing::warn!(error = %e, url = %url, "token proxy request failed");
            json_response(503, &serde_json::json!({"error": "token_proxy_unreachable"}))
        }
    }
}

fn redirect_to(url: &str, query: Option<&str>) -> Rejection {
    let target = match query {
        Some(qs) if !qs.is_empty() => format!("{url}?{qs}"),
        _ => url.to_owned(),
    };
    tracing::debug!(target = %target, "redirecting to issuer");
    Rejection::status(302)
        .with_header("location", &target)
        .with_header("access-control-allow-origin", "*")
}

fn json_response(status: u16, value: &serde_json::Value) -> Rejection {
    let body = Bytes::from(value.to_string());
    Rejection::status(status)
        .with_header("content-type", "application/json")
        .with_header("access-control-allow-origin", "*")
        .with_body(body)
}
