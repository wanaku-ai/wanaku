use bytes::Bytes;
use http::StatusCode;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext, Rejection};
use wanaku_types::config::ENV;

use crate::IssuerConfig;

wanaku_filters::body_filter_boilerplate!(WellKnownFilter, "wanaku_well_known");

const WELL_KNOWN_PREFIX: &str = "/.well-known/oauth-protected-resource/";

/// The metadata URLs depend on these headers, so caches must key on them.
const FORWARDING_VARY: &str = "x-forwarded-proto, x-forwarded-host";

impl WellKnownFilter {
    #[expect(
        clippy::too_many_lines,
        reason = "route dispatch with multiple well-known endpoints"
    )]
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

        let base = public_base(ENV.mcp_public_url.as_deref(), &ctx.request.headers);

        match path {
            "/.well-known/openid-configuration" | "/.well-known/oauth-authorization-server" => Ok(
                FilterAction::Reject(self.serve_discovery(&base, &issuer_config)),
            ),
            "/authorize" => Ok(FilterAction::Reject(redirect_to(
                &format!("{}/protocol/openid-connect/auth", issuer_config.issuer),
                ctx.request.uri.query(),
            ))),
            "/token" => {
                let result = proxy_token_endpoint(&issuer_config, body).await;
                Ok(FilterAction::Reject(result))
            }
            "/register" => Ok(FilterAction::Reject(redirect_to(
                &format!(
                    "{}/clients-registrations/openid-connect",
                    issuer_config.issuer
                ),
                ctx.request.uri.query(),
            ))),
            _ => Ok(FilterAction::Continue),
        }
    }

    #[expect(
        clippy::unused_self,
        reason = "method on impl for consistency with other handlers"
    )]
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
        json_response(StatusCode::OK, &doc).with_header("vary", FORWARDING_VARY)
    }

    #[expect(
        clippy::unused_self,
        reason = "method on impl for consistency with other handlers"
    )]
    fn handle_protected_resource_metadata(
        &self,
        ctx: &HttpFilterContext<'_>,
        suffix: &str,
    ) -> Result<FilterAction, FilterError> {
        let base = public_base(ENV.mcp_public_url.as_deref(), &ctx.request.headers);
        let resource = resource_url(&base, suffix);
        let metadata = protected_resource_metadata(&resource, ctx.extensions.get::<IssuerConfig>());

        tracing::debug!(resource = %resource, "served protected resource metadata");
        Ok(FilterAction::Reject(
            json_response(StatusCode::OK, &metadata).with_header("vary", FORWARDING_VARY),
        ))
    }
}

/// RFC 9728 metadata for `resource`.
///
/// `authorization_servers` names the public issuer itself. A client gets the
/// authorization server metadata from the issuer, so the `issuer` value in that
/// metadata matches the identifier the client used (RFC 8414, section 3.3).
fn protected_resource_metadata(
    resource: &str,
    issuer_config: Option<&IssuerConfig>,
) -> serde_json::Value {
    let auth_servers: Vec<&str> = issuer_config
        .map(|c| c.issuer.as_str())
        .filter(|issuer| !issuer.is_empty())
        .into_iter()
        .collect();
    serde_json::json!({
        "resource": resource,
        "authorization_servers": auth_servers,
        "bearer_methods_supported": ["header"],
    })
}

/// Base URL that clients use to reach the MCP listener.
///
/// `WANAKU_MCP_PUBLIC_URL` wins. Otherwise the scheme and host come from the
/// `X-Forwarded-Proto` and `X-Forwarded-Host` headers that a TLS-terminating
/// proxy sets, then from `http://` and the `Host` header.
fn public_base(public_url: Option<&str>, headers: &http::HeaderMap) -> String {
    if let Some(url) = public_url {
        return url.to_owned();
    }
    let first = |name: &str| {
        headers
            .get(name)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.split(',').next())
            .map(str::trim)
            .filter(|v| !v.is_empty())
    };
    let scheme = match first("x-forwarded-proto") {
        Some(proto) if proto.eq_ignore_ascii_case("https") => "https",
        _ => "http",
    };
    let host = first("x-forwarded-host")
        .filter(|h| !h.contains(['/', '\\', '@', ' ', '?', '#']))
        .or_else(|| first(http::header::HOST.as_str()))
        .unwrap_or("localhost:8081");
    format!("{scheme}://{host}")
}

/// Preserve concrete and virtual MCP endpoint paths in resource metadata.
fn resource_url(base: &str, suffix: &str) -> String {
    let namespace = suffix
        .strip_suffix("/mcp")
        .or_else(|| suffix.strip_suffix("/mcp/"))
        .filter(|ns| {
            let name = ns.strip_prefix("virtual-ns/").unwrap_or(ns);
            wanaku_types::registry::validate_namespace_name(name).is_ok()
        })
        .unwrap_or("default");
    format!("{base}/{namespace}/mcp")
}

#[expect(
    clippy::too_many_lines,
    clippy::cognitive_complexity,
    reason = "HTTP proxy with error handling"
)]
async fn proxy_token_endpoint(config: &IssuerConfig, body: &Option<Bytes>) -> Rejection {
    let url = format!("{}/protocol/openid-connect/token", config.upstream_issuer);

    let req_body = body.as_ref().map(|b| b.to_vec()).unwrap_or_default();

    let client = match reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(10))
        .build()
    {
        Ok(c) => c,
        Err(e) => {
            tracing::warn!(error = %e, "failed to create HTTP client for token proxy");
            return json_response(
                StatusCode::SERVICE_UNAVAILABLE,
                &serde_json::json!({"error": "token_proxy_error"}),
            );
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
                Ok(resp_body) => Rejection::status(status)
                    .with_header("content-type", "application/json")
                    .with_header("access-control-allow-origin", ENV.cors_origin.as_str())
                    .with_body(resp_body),
                Err(e) => {
                    tracing::warn!(error = %e, "failed to read token response");
                    json_response(
                        StatusCode::SERVICE_UNAVAILABLE,
                        &serde_json::json!({"error": "token_proxy_read_error"}),
                    )
                }
            }
        }
        Err(e) => {
            tracing::warn!(error = %e, url = %url, "token proxy request failed");
            json_response(
                StatusCode::SERVICE_UNAVAILABLE,
                &serde_json::json!({"error": "token_proxy_unreachable"}),
            )
        }
    }
}

fn redirect_to(url: &str, query: Option<&str>) -> Rejection {
    let target = match query {
        Some(qs) if !qs.is_empty() => format!("{url}?{qs}"),
        _ => url.to_owned(),
    };
    tracing::debug!(target = %target, "redirecting to issuer");
    Rejection::status(StatusCode::FOUND.as_u16())
        .with_header("location", &target)
        .with_header("access-control-allow-origin", ENV.cors_origin.as_str())
}

fn json_response(status: StatusCode, value: &serde_json::Value) -> Rejection {
    let body = Bytes::from(value.to_string());
    Rejection::status(status.as_u16())
        .with_header("content-type", "application/json")
        .with_header("access-control-allow-origin", ENV.cors_origin.as_str())
        .with_body(body)
}

#[cfg(test)]
mod tests {
    use super::{
        IssuerConfig, WellKnownFilter, protected_resource_metadata, proxy_token_endpoint,
        public_base, resource_url,
    };
    use bytes::Bytes;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    fn headers(pairs: &[(&'static str, &'static str)]) -> http::HeaderMap {
        pairs
            .iter()
            .map(|(name, value)| {
                (
                    http::HeaderName::from_static(name),
                    http::HeaderValue::from_static(value),
                )
            })
            .collect()
    }

    #[test]
    fn resource_always_names_the_namespace() {
        let base = "http://h:1";
        assert_eq!(resource_url(base, "mcp"), "http://h:1/default/mcp");
        assert_eq!(resource_url(base, "default/mcp"), "http://h:1/default/mcp");
        assert_eq!(resource_url(base, "finance/mcp"), "http://h:1/finance/mcp");
        assert_eq!(resource_url(base, "a/b/mcp"), "http://h:1/default/mcp");
        assert_eq!(
            resource_url(base, "virtual-ns/integration/mcp"),
            "http://h:1/virtual-ns/integration/mcp"
        );
    }

    #[test]
    fn base_without_forwarding_headers_uses_http_and_host() {
        let base = public_base(None, &headers(&[("host", "localhost:8081")]));
        assert_eq!(base, "http://localhost:8081");
        assert_eq!(
            resource_url(&base, "mcp"),
            "http://localhost:8081/default/mcp"
        );
        assert_eq!(
            resource_url(&base, "finance/mcp"),
            "http://localhost:8081/finance/mcp"
        );
        assert_eq!(public_base(None, &headers(&[])), "http://localhost:8081");
    }

    #[test]
    fn base_behind_tls_proxy_uses_forwarded_scheme_and_host() {
        let forwarded = headers(&[
            ("host", "wanaku-mcp:8081"),
            ("x-forwarded-proto", "https"),
            ("x-forwarded-host", "mcp.apps.example.com"),
        ]);
        let base = public_base(None, &forwarded);
        assert_eq!(base, "https://mcp.apps.example.com");
        assert_eq!(
            resource_url(&base, "default/mcp"),
            "https://mcp.apps.example.com/default/mcp"
        );
        assert_eq!(
            resource_url(&base, "finance/mcp"),
            "https://mcp.apps.example.com/finance/mcp"
        );
    }

    #[test]
    fn base_uses_first_forwarded_value_and_ignores_bad_values() {
        let proto_only = headers(&[("host", "h:1"), ("x-forwarded-proto", "HTTPS, http")]);
        assert_eq!(public_base(None, &proto_only), "https://h:1");
        let bad = headers(&[
            ("host", "h:1"),
            ("x-forwarded-proto", "ftp"),
            ("x-forwarded-host", "evil.example/path"),
        ]);
        assert_eq!(public_base(None, &bad), "http://h:1");
        for host in ["h:2?x=1", "h:2#frag"] {
            let mut query = headers(&[("host", "h:1")]);
            query.insert("x-forwarded-host", http::HeaderValue::from_static(host));
            assert_eq!(public_base(None, &query), "http://h:1");
        }
    }

    #[test]
    fn public_url_overrides_forwarding_headers() {
        let forwarded = headers(&[("x-forwarded-proto", "http"), ("x-forwarded-host", "h:1")]);
        let base = public_base(Some("https://mcp.example.com/proxy"), &forwarded);
        assert_eq!(
            resource_url(&base, "finance/mcp"),
            "https://mcp.example.com/proxy/finance/mcp"
        );
    }

    #[test]
    fn authorization_server_is_the_public_issuer() {
        let config = IssuerConfig::new(
            "http://localhost:8543/realms/wanaku".into(),
            Some("http://keycloak:8080/realms/wanaku".into()),
        );
        let doc = protected_resource_metadata("http://localhost:4181/default/mcp", Some(&config));
        assert_eq!(doc["resource"], "http://localhost:4181/default/mcp");
        // The client follows this entry and gets the metadata from the issuer,
        // whose `issuer` value is the same URL (RFC 8414, section 3.3).
        assert_eq!(
            doc["authorization_servers"],
            serde_json::json!([config.issuer])
        );
    }

    #[test]
    fn no_authorization_server_without_issuer() {
        let empty = IssuerConfig::new(String::new(), None);
        for config in [None, Some(&empty)] {
            let doc = protected_resource_metadata("http://h:1/default/mcp", config);
            assert_eq!(doc["authorization_servers"], serde_json::json!([]));
        }
    }

    #[test]
    fn discovery_uses_public_issuer() -> Result<(), Box<dyn std::error::Error>> {
        let config = IssuerConfig::new(
            "http://localhost:8543/realms/wanaku".into(),
            Some("http://keycloak:8080/realms/wanaku".into()),
        );
        let filter = WellKnownFilter {
            max_body_bytes: 1024,
        };
        let response = filter.serve_discovery("http://localhost:4180", &config);
        let doc: serde_json::Value =
            serde_json::from_slice(response.body.as_deref().ok_or("missing discovery body")?)?;
        assert!(
            response
                .headers
                .iter()
                .any(|(name, value)| name == "vary" && value == super::FORWARDING_VARY)
        );
        assert_eq!(doc["issuer"], config.issuer);
        assert_eq!(
            doc["jwks_uri"],
            "http://localhost:8543/realms/wanaku/protocol/openid-connect/certs"
        );
        assert_eq!(
            doc["authorization_endpoint"],
            "http://localhost:4180/authorize"
        );
        assert_eq!(doc["token_endpoint"], "http://localhost:4180/token");
        assert!(!doc.to_string().contains("keycloak:8080"));
        Ok(())
    }

    #[tokio::test]
    async fn token_proxy_uses_internal_issuer() -> Result<(), Box<dyn std::error::Error>> {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await?;
        let config = IssuerConfig::new(
            "http://public-issuer.invalid/realms/wanaku".into(),
            Some(format!("http://{}/realms/wanaku", listener.local_addr()?)),
        );
        let body = Some(Bytes::from_static(
            b"grant_type=refresh_token&refresh_token=test",
        ));
        let upstream = async {
            let (mut stream, _) = listener.accept().await?;
            let mut request = Vec::new();
            let mut buffer = [0; 1024];
            while !request.ends_with(b"refresh_token=test") {
                let count = stream.read(&mut buffer).await?;
                if count == 0 {
                    return Err(std::io::Error::from(std::io::ErrorKind::UnexpectedEof));
                }
                request.extend_from_slice(&buffer[..count]);
            }
            assert!(
                request
                    .starts_with(b"POST /realms/wanaku/protocol/openid-connect/token HTTP/1.1\r\n")
            );
            stream
                .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")
                .await?;
            Ok::<_, std::io::Error>(())
        };
        let (result, response) = tokio::time::timeout(std::time::Duration::from_secs(5), async {
            tokio::join!(upstream, proxy_token_endpoint(&config, &body))
        })
        .await?;
        result?;
        assert_eq!(response.status, 200);
        assert_eq!(response.body.as_deref(), Some(b"{}".as_slice()));
        Ok(())
    }
}
