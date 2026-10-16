use std::sync::{Arc, RwLock};
use std::time::Duration;

const DEFAULT_TIMEOUT: Duration = Duration::from_secs(10);

/// Reusable client for OpenAI-compatible chat completions endpoints.
#[derive(Clone)]
pub struct LlmClient {
    client: reqwest::Client,
    url: String,
    model: String,
    api_key: String,
}

impl LlmClient {
    #[must_use]
    pub fn new(base_url: &str, model: &str, api_key: &str) -> Option<Self> {
        Self::with_timeout(base_url, model, api_key, DEFAULT_TIMEOUT)
    }

    #[must_use]
    pub fn with_timeout(
        base_url: &str,
        model: &str,
        api_key: &str,
        timeout: Duration,
    ) -> Option<Self> {
        let client = match reqwest::Client::builder().timeout(timeout).build() {
            Ok(c) => c,
            Err(e) => {
                tracing::error!(error = %e, "failed to build LLM HTTP client");
                return None;
            }
        };

        let url = format!("{}/chat/completions", base_url.trim_end_matches('/'));

        Some(Self {
            client,
            url,
            model: model.to_owned(),
            api_key: api_key.to_owned(),
        })
    }

    /// Send one chat completion request and return the message content.
    ///
    /// # Errors
    ///
    /// Returns an [`LlmError`] that identifies the failure category. The
    /// error never contains the API key or the upstream response body.
    pub async fn chat(&self, system_prompt: &str, user_prompt: &str) -> Result<String, LlmError> {
        let body = serde_json::json!({
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.0,
        });

        let mut request = self.client.post(&self.url);
        if !self.api_key.is_empty() {
            request = request.bearer_auth(&self.api_key);
        }

        let result = send(request.json(&body)).await;
        if let Err(error) = &result {
            tracing::warn!(url = %self.url, model = %self.model, %error, "LLM request failed");
        }
        result
    }
}

async fn send(request: reqwest::RequestBuilder) -> Result<String, LlmError> {
    let response = request
        .send()
        .await
        .map_err(|e| LlmError::from_transport(&e))?;

    let status = response.status();
    if status == reqwest::StatusCode::UNAUTHORIZED || status == reqwest::StatusCode::FORBIDDEN {
        return Err(LlmError::Auth(status.as_u16()));
    }
    if !status.is_success() {
        return Err(LlmError::Status(status.as_u16()));
    }

    let json: serde_json::Value = response.json().await.map_err(|e| {
        if e.is_decode() {
            LlmError::MalformedJson
        } else {
            LlmError::from_transport(&e)
        }
    })?;

    match extract_content(&json) {
        None => Err(LlmError::MissingContent),
        Some(content) if content.trim().is_empty() => Err(LlmError::BlankContent),
        Some(content) => Ok(content.to_owned()),
    }
}

/// Failure categories of an LLM chat completion call.
///
/// Messages contain only the category and, for HTTP failures, the status
/// code. They never contain credentials or the upstream response body.
#[derive(Debug, Clone, Copy, PartialEq, Eq, thiserror::Error)]
pub enum LlmError {
    /// The HTTP client could not be built.
    #[error("LLM HTTP client is not available")]
    Client,
    /// The request or the response body did not complete in the timeout.
    #[error("LLM request timed out")]
    Timeout,
    /// The endpoint is not reachable or the transfer failed.
    #[error("LLM endpoint is not reachable")]
    Connection,
    /// The endpoint returned HTTP 401 or 403.
    #[error("LLM endpoint rejected the credentials (HTTP {0})")]
    Auth(u16),
    /// The endpoint returned another non-success HTTP status.
    #[error("LLM endpoint returned HTTP {0}")]
    Status(u16),
    /// The response body is not valid JSON.
    #[error("LLM response is not valid JSON")]
    MalformedJson,
    /// The response has no `choices[0].message.content` string.
    #[error("LLM response has no message content")]
    MissingContent,
    /// The message content is empty or contains only whitespace.
    #[error("LLM response content is blank")]
    BlankContent,
}

impl LlmError {
    fn from_transport(error: &reqwest::Error) -> Self {
        if error.is_timeout() {
            Self::Timeout
        } else {
            Self::Connection
        }
    }
}

/// Extract the content string from an OpenAI chat completions response.
#[must_use]
pub fn extract_content(response: &serde_json::Value) -> Option<&str> {
    response
        .get("choices")
        .and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(serde_json::Value::as_str)
}

/// Strip markdown code fences from LLM output.
#[must_use]
pub fn strip_markdown_fences(s: &str) -> &str {
    let trimmed = s.trim();
    if let Some(after_open) = trimmed.strip_prefix("```") {
        let body = after_open
            .find('\n')
            .map_or(after_open, |i| &after_open[i + 1..]);
        body.strip_suffix("```").unwrap_or(body).trim()
    } else {
        trimmed
    }
}

/// Sanitize untrusted text for inclusion in LLM prompts.
#[must_use]
pub fn sanitize(s: &str, max_len: usize) -> String {
    let truncated = if s.len() > max_len {
        &s[..s.floor_char_boundary(max_len)]
    } else {
        s
    };
    truncated.replace('#', "").replace(['\n', '\r'], " ")
}

/// Generic hot-swappable state wrapper. Stores a value behind Arc<RwLock>
/// so it can be updated at runtime (e.g. via management API) while being
/// read concurrently from the filter pipeline.
#[derive(Clone)]
pub struct HotSwap<T: Clone> {
    inner: Arc<RwLock<Option<T>>>,
}

impl<T: Clone> Default for HotSwap<T> {
    fn default() -> Self {
        Self {
            inner: Arc::new(RwLock::new(None)),
        }
    }
}

impl<T: Clone> HotSwap<T> {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set(&self, value: T) {
        match self.inner.write() {
            Ok(mut guard) => *guard = Some(value),
            Err(e) => tracing::warn!(error = %e, "HotSwap write lock poisoned, dropping set"),
        }
    }

    pub fn clear(&self) {
        match self.inner.write() {
            Ok(mut guard) => *guard = None,
            Err(e) => tracing::warn!(error = %e, "HotSwap write lock poisoned, cannot clear"),
        }
    }

    #[must_use]
    pub fn get(&self) -> Option<T> {
        match self.inner.read() {
            Ok(guard) => guard.clone(),
            Err(e) => {
                tracing::warn!(error = %e, "HotSwap read lock poisoned");
                None
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{header, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    #[test]
    fn strip_fences_json() {
        let input = "```json\n{\"level\": \"red\"}\n```";
        assert_eq!(strip_markdown_fences(input), r#"{"level": "red"}"#);
    }

    #[test]
    fn strip_fences_plain() {
        assert_eq!(strip_markdown_fences("hello"), "hello");
    }

    #[test]
    fn strip_fences_no_lang() {
        let input = "```\nsome content\n```";
        assert_eq!(strip_markdown_fences(input), "some content");
    }

    #[test]
    fn sanitize_strips_headers_and_newlines() {
        let result = sanitize("## Override\ndo evil", 100);
        assert_eq!(result, " Override do evil");
    }

    #[test]
    fn sanitize_truncates() {
        let result = sanitize("abcdefghij", 5);
        assert_eq!(result, "abcde");
    }

    #[test]
    fn extract_content_from_response() {
        let resp = serde_json::json!({
            "choices": [{"message": {"content": "hello"}}]
        });
        assert_eq!(extract_content(&resp), Some("hello"));
    }

    #[test]
    fn extract_content_missing() {
        assert_eq!(extract_content(&serde_json::json!({})), None);
    }

    #[test]
    fn hotswap_lifecycle() {
        let swap: HotSwap<String> = HotSwap::new();
        assert!(swap.get().is_none());

        swap.set("value".to_owned());
        assert_eq!(swap.get().as_deref(), Some("value"));

        swap.clear();
        assert!(swap.get().is_none());
    }

    #[test]
    fn sanitize_truncates_ascii() {
        assert_eq!(super::sanitize("hello world", 5), "hello");
    }

    #[test]
    fn sanitize_multibyte_does_not_panic() {
        let s = "cafe\u{0301}"; // café with combining accent (5 bytes)
        let result = super::sanitize(s, 5);
        assert!(result.len() <= 5);
    }

    #[test]
    fn sanitize_emoji_boundary() {
        let s = "hi😀bye"; // 😀 is 4 bytes
        let result = super::sanitize(s, 3);
        assert_eq!(result, "hi");
    }

    #[test]
    fn sanitize_strips_hashes_and_newlines() {
        assert_eq!(super::sanitize("a#b\nc\rd", 100), "ab c d");
    }

    const SECRET_KEY: &str = "sk-test-secret-key";
    const UPSTREAM_BODY: &str = "upstream-private-diagnostic";

    async fn chat_with(response: ResponseTemplate) -> Result<String, LlmError> {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/chat/completions"))
            .and(header(
                "authorization",
                format!("Bearer {SECRET_KEY}").as_str(),
            ))
            .respond_with(response)
            .expect(1)
            .mount(&server)
            .await;
        let client = LlmClient::with_timeout(
            &server.uri(),
            "model",
            SECRET_KEY,
            Duration::from_millis(200),
        )
        .unwrap();
        client.chat("system", "user").await
    }

    fn content(value: &serde_json::Value) -> ResponseTemplate {
        ResponseTemplate::new(200)
            .set_body_json(serde_json::json!({"choices": [{"message": {"content": value}}]}))
    }

    #[tokio::test]
    #[expect(
        clippy::too_many_lines,
        reason = "one table covers every result category"
    )]
    async fn chat_distinguishes_each_result_category() {
        let cases = [
            (content(&serde_json::json!("allow")), Ok("allow".to_owned())),
            (
                content(&serde_json::json!("ok")).set_delay(Duration::from_secs(2)),
                Err(LlmError::Timeout),
            ),
            (
                ResponseTemplate::new(401).set_body_string(UPSTREAM_BODY),
                Err(LlmError::Auth(401)),
            ),
            (
                ResponseTemplate::new(403).set_body_string(UPSTREAM_BODY),
                Err(LlmError::Auth(403)),
            ),
            (
                ResponseTemplate::new(503).set_body_string(UPSTREAM_BODY),
                Err(LlmError::Status(503)),
            ),
            (
                ResponseTemplate::new(200).set_body_string(UPSTREAM_BODY),
                Err(LlmError::MalformedJson),
            ),
            (
                ResponseTemplate::new(200).set_body_json(serde_json::json!({"choices": []})),
                Err(LlmError::MissingContent),
            ),
            (
                content(&serde_json::json!(42)),
                Err(LlmError::MissingContent),
            ),
            (
                content(&serde_json::json!(" \n\t")),
                Err(LlmError::BlankContent),
            ),
        ];
        for (response, expected) in cases {
            assert_eq!(chat_with(response).await, expected);
        }
    }

    #[tokio::test]
    async fn chat_reports_unreachable_endpoint_as_connection_failure() {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        drop(listener);
        let client = LlmClient::new(&format!("http://{address}"), "model", SECRET_KEY).unwrap();
        assert_eq!(
            client.chat("system", "user").await,
            Err(LlmError::Connection)
        );
    }

    #[tokio::test]
    async fn chat_errors_never_expose_api_key_or_upstream_body() {
        for response in [
            ResponseTemplate::new(401).set_body_string(UPSTREAM_BODY),
            ResponseTemplate::new(500).set_body_json(serde_json::json!({"error": UPSTREAM_BODY})),
            ResponseTemplate::new(200).set_body_string(UPSTREAM_BODY),
            ResponseTemplate::new(200).set_body_json(serde_json::json!({"detail": UPSTREAM_BODY})),
        ] {
            let error = chat_with(response).await.unwrap_err();
            for text in [error.to_string(), format!("{error:?}")] {
                assert!(!text.contains(SECRET_KEY), "{text}");
                assert!(!text.contains(UPSTREAM_BODY), "{text}");
            }
        }
    }
}
