use bytes::Bytes;
use http::StatusCode;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext, Rejection};
use wanaku_types::config::ENV;

use crate::IssuerConfig;

wanaku_filters::body_filter_boilerplate!(WellKnownFilter, "wanaku_well_known");

const WELL_KNOWN_PREFIX: &str = "/.well-known/oauth-protected-resource/";

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

        let host = ctx
            .request
            .headers
            .get(http::header::HOST)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("localhost:8081");
        let base = format!("http://{host}");

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
        json_response(StatusCode::OK, &doc)
    }

    #[expect(
        clippy::unused_self,
        reason = "method on impl for consistency with other handlers"
    )]
    #[expect(
        clippy::too_many_lines,
        reason = "RFC 9728 protected resource metadata assembly"
    )]
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
        let has_issuer = issuer_config.is_some_and(|c| !c.issuer.is_empty());

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
        Ok(FilterAction::Reject(json_response(
            StatusCode::OK,
            &metadata,
        )))
    }
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
    use super::{IssuerConfig, WellKnownFilter, proxy_token_endpoint};
    use bytes::Bytes;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

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
