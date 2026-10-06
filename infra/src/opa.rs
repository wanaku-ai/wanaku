use std::time::Duration;

/// Largest OPA response body that the client accepts.
pub const MAX_RESPONSE_BYTES: usize = 1024 * 1024;

/// Failure categories of an OPA Data API query.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OpaError {
    /// OPA cannot be reached.
    Unavailable,
    /// OPA did not answer in the configured timeout.
    Timeout,
    /// OPA rejected the credentials (HTTP 401 or 403).
    Auth,
    /// OPA returned another error status, for example a policy evaluation error.
    Policy,
    /// OPA returned a body that is too large or is not a JSON object.
    InvalidResponse,
}

/// HTTP client for the OPA REST Data API.
#[derive(Debug, Clone)]
pub struct OpaClient {
    client: reqwest::Client,
    url: String,
    token: Option<String>,
}

impl OpaClient {
    /// Build a client. `ca_cert_pem` adds a trusted root certificate for
    /// remote OPA endpoints that use a private certificate authority.
    pub fn new(
        base_url: &str,
        token: Option<&str>,
        timeout: Duration,
        ca_cert_pem: Option<&[u8]>,
    ) -> Result<Self, String> {
        let mut builder = reqwest::Client::builder().timeout(timeout);
        if let Some(pem) = ca_cert_pem {
            let certificates = reqwest::Certificate::from_pem_bundle(pem)
                .map_err(|error| format!("invalid OPA CA certificate: {error}"))?;
            if certificates.is_empty() {
                return Err("OPA CA certificate file contains no PEM certificate".to_owned());
            }
            for certificate in certificates {
                builder = builder.add_root_certificate(certificate);
            }
        }
        let client = builder
            .build()
            .map_err(|error| format!("failed to build OPA HTTP client: {error}"))?;
        Ok(Self {
            client,
            url: base_url.trim_end_matches('/').to_owned(),
            token: token.filter(|token| !token.is_empty()).map(str::to_owned),
        })
    }

    /// Query `POST /v1/data/<decision_path>` with `input`.
    ///
    /// Returns `Ok(None)` when the decision is undefined, that is, when the
    /// response has no `result` member.
    pub async fn query(
        &self,
        decision_path: &str,
        input: &serde_json::Value,
    ) -> Result<Option<serde_json::Value>, OpaError> {
        let mut request = self
            .client
            .post(format!("{}/v1/data/{decision_path}", self.url))
            .json(&serde_json::json!({ "input": input }));
        if let Some(token) = &self.token {
            request = request.bearer_auth(token);
        }
        let response = request
            .send()
            .await
            .map_err(|error| request_error(&error))?;
        let status = response.status();
        if status == reqwest::StatusCode::UNAUTHORIZED || status == reqwest::StatusCode::FORBIDDEN {
            tracing::warn!(%status, decision_path, "OPA rejected the credentials");
            return Err(OpaError::Auth);
        }
        if !status.is_success() {
            tracing::warn!(%status, decision_path, "OPA returned an error status");
            return Err(OpaError::Policy);
        }
        read_result(response).await
    }
}

/// Read the `result` member of a successful response, within the size limit.
async fn read_result(response: reqwest::Response) -> Result<Option<serde_json::Value>, OpaError> {
    if response
        .content_length()
        .is_some_and(|length| length > MAX_RESPONSE_BYTES as u64)
    {
        return Err(OpaError::InvalidResponse);
    }
    let body = response
        .bytes()
        .await
        .map_err(|error| request_error(&error))?;
    if body.len() > MAX_RESPONSE_BYTES {
        return Err(OpaError::InvalidResponse);
    }
    match serde_json::from_slice::<serde_json::Value>(&body) {
        Ok(serde_json::Value::Object(mut response)) => Ok(response.remove("result")),
        _ => Err(OpaError::InvalidResponse),
    }
}

fn request_error(error: &reqwest::Error) -> OpaError {
    if error.is_timeout() {
        OpaError::Timeout
    } else {
        tracing::warn!(error = %error, "OPA request failed");
        OpaError::Unavailable
    }
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use wiremock::matchers::{body_json, header, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    use super::{MAX_RESPONSE_BYTES, OpaClient, OpaError};

    fn client(url: &str, token: Option<&str>) -> OpaClient {
        OpaClient::new(url, token, Duration::from_millis(200), None).unwrap()
    }

    async fn respond(template: ResponseTemplate) -> Result<Option<serde_json::Value>, OpaError> {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .respond_with(template)
            .mount(&server)
            .await;
        client(&server.uri(), None)
            .query("wanaku/allow", &serde_json::json!({}))
            .await
    }

    #[tokio::test]
    async fn sends_input_envelope_and_bearer_token() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/v1/data/wanaku/tool_call/allow"))
            .and(header("authorization", "Bearer opa-token"))
            .and(body_json(serde_json::json!({ "input": { "amount": 10 } })))
            .respond_with(
                ResponseTemplate::new(200).set_body_json(serde_json::json!({ "result": true })),
            )
            .expect(1)
            .mount(&server)
            .await;
        let result = client(&format!("{}/", server.uri()), Some("opa-token"))
            .query(
                "wanaku/tool_call/allow",
                &serde_json::json!({ "amount": 10 }),
            )
            .await;
        assert_eq!(result, Ok(Some(serde_json::json!(true))));
    }

    #[tokio::test]
    async fn missing_result_is_undefined() {
        let result = respond(ResponseTemplate::new(200).set_body_json(serde_json::json!({}))).await;
        assert_eq!(result, Ok(None));
    }

    #[tokio::test]
    async fn classifies_failures() {
        for (template, expected) in [
            (ResponseTemplate::new(401), OpaError::Auth),
            (ResponseTemplate::new(403), OpaError::Auth),
            (ResponseTemplate::new(500), OpaError::Policy),
            (ResponseTemplate::new(404), OpaError::Policy),
            (
                ResponseTemplate::new(200).set_body_string("not json"),
                OpaError::InvalidResponse,
            ),
            (
                ResponseTemplate::new(200).set_body_json(serde_json::json!([true])),
                OpaError::InvalidResponse,
            ),
            (
                ResponseTemplate::new(200).set_body_string(" ".repeat(MAX_RESPONSE_BYTES + 1)),
                OpaError::InvalidResponse,
            ),
            (
                ResponseTemplate::new(200).set_delay(Duration::from_secs(2)),
                OpaError::Timeout,
            ),
        ] {
            assert_eq!(respond(template).await, Err(expected));
        }
    }

    #[tokio::test]
    async fn unreachable_server_is_unavailable() {
        let result = client("http://127.0.0.1:9", None)
            .query("wanaku/allow", &serde_json::json!({}))
            .await;
        assert_eq!(result, Err(OpaError::Unavailable));
    }

    #[test]
    fn invalid_ca_certificate_is_rejected() {
        let result = OpaClient::new(
            "https://opa.example",
            None,
            Duration::from_secs(1),
            Some(b"not a certificate"),
        );
        assert!(result.is_err());
    }

    #[test]
    fn valid_ca_certificate_is_accepted() {
        const CA: &[u8] = include_bytes!("../tests/fixtures/opa-ca.pem");
        assert!(
            OpaClient::new(
                "https://opa.example",
                None,
                Duration::from_secs(1),
                Some(CA)
            )
            .is_ok()
        );
    }
}
