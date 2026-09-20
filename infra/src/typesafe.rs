use std::time::Duration;

/// HTTP client for the TypeSafe System One evaluation API.
#[derive(Clone)]
pub struct SystemOneClient {
    client: reqwest::Client,
    url: String,
    model: String,
    api_key: String,
}

impl SystemOneClient {
    #[must_use]
    pub fn new(base_url: &str, model: &str, api_key: &str) -> Option<Self> {
        let client = match reqwest::Client::builder()
            .timeout(Duration::from_secs(10))
            .build()
        {
            Ok(client) => client,
            Err(error) => {
                tracing::error!(error = %error, "failed to build TypeSafe System One HTTP client");
                return None;
            }
        };
        Some(Self {
            client,
            url: format!("{}/v1/systemone", base_url.trim_end_matches('/')),
            model: model.to_owned(),
            api_key: api_key.to_owned(),
        })
    }

    pub async fn evaluate(
        &self,
        state: &serde_json::Value,
        question_id: &str,
        question: &serde_json::Value,
    ) -> Result<serde_json::Value, String> {
        let body = serde_json::json!({
            "state": state,
            "model": self.model,
            "questions": { question_id: question },
        });
        let response = self
            .client
            .post(&self.url)
            .bearer_auth(&self.api_key)
            .json(&body)
            .send()
            .await
            .map_err(|error| format!("TypeSafe System One request failed: {error}"))?;
        if !response.status().is_success() {
            return Err(format!(
                "TypeSafe System One returned HTTP {}",
                response.status()
            ));
        }
        response
            .json()
            .await
            .map_err(|error| format!("failed to parse TypeSafe System One response: {error}"))
    }
}

#[cfg(test)]
mod tests {
    use wiremock::matchers::{body_json, header, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    use super::SystemOneClient;

    #[tokio::test]
    async fn sends_a_system_one_request() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/v1/systemone"))
            .and(header("authorization", "Bearer test-key"))
            .and(body_json(serde_json::json!({
                "state": { "tool": "restart" },
                "model": "jev-latest",
                "questions": {
                    "is_safe": {
                        "type": "noul",
                        "instructions": "Is the tool safe?"
                    }
                }
            })))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "model": "jev-1.13.0",
                "answers": { "is_safe": { "type": "noul", "noul": 0.9 } }
            })))
            .expect(1)
            .mount(&server)
            .await;

        let client = SystemOneClient::new(&server.uri(), "jev-latest", "test-key")
            .expect("client construction");
        let result = client
            .evaluate(
                &serde_json::json!({ "tool": "restart" }),
                "is_safe",
                &serde_json::json!({ "type": "noul", "instructions": "Is the tool safe?" }),
            )
            .await
            .expect("successful response");
        assert_eq!(result["answers"]["is_safe"]["noul"], 0.9);
    }
}
