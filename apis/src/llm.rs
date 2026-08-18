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

    pub async fn chat(
        &self,
        system_prompt: &str,
        user_prompt: &str,
    ) -> Option<String> {
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

        let response = match request.json(&body).send().await {
            Ok(r) => r,
            Err(e) => {
                tracing::warn!(error = %e, "LLM request failed");
                return None;
            }
        };

        if !response.status().is_success() {
            tracing::warn!(status = %response.status(), "LLM returned non-success status");
            return None;
        }

        let json: serde_json::Value = match response.json().await {
            Ok(b) => b,
            Err(e) => {
                tracing::warn!(error = %e, "LLM response parse failed");
                return None;
            }
        };

        extract_content(&json).map(String::from)
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
    let truncated = if s.len() > max_len { &s[..s.floor_char_boundary(max_len)] } else { s };
    truncated
        .replace('#', "")
        .replace('\n', " ")
        .replace('\r', " ")
}

/// Generic hot-swappable state wrapper. Stores a value behind Arc<RwLock>
/// so it can be updated at runtime (e.g. via management API) while being
/// read concurrently from the filter pipeline.
#[derive(Clone)]
pub struct HotSwap<T: Clone> {
    inner: Arc<RwLock<Option<T>>>,
}

impl<T: Clone> HotSwap<T> {
    #[must_use]
    pub fn new() -> Self {
        Self {
            inner: Arc::new(RwLock::new(None)),
        }
    }

    pub fn set(&self, value: T) {
        if let Ok(mut guard) = self.inner.write() {
            *guard = Some(value);
        }
    }

    pub fn clear(&self) {
        if let Ok(mut guard) = self.inner.write() {
            *guard = None;
        }
    }

    #[must_use]
    pub fn get(&self) -> Option<T> {
        self.inner.read().ok().and_then(|guard| guard.clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

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
}
