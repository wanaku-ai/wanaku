use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct AgentEntry {
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default = "default_namespace")]
    pub namespace: String,
    pub address: String,
    #[serde(
        default,
        rename = "cardAddress",
        skip_serializing_if = "Option::is_none"
    )]
    pub card_address: Option<String>,
}

fn default_namespace() -> String {
    crate::registry::DEFAULT_NAMESPACE.to_owned()
}

#[derive(Debug, Clone, Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct AgentView {
    pub name: String,
    pub description: String,
    pub namespace: String,
    pub address: String,
    #[serde(rename = "cardAddress", skip_serializing_if = "Option::is_none")]
    pub card_address: Option<String>,
    #[serde(rename = "proxyUrl")]
    pub proxy_url: String,
}

impl AgentEntry {
    pub fn validate(&self) -> Result<(), String> {
        crate::registry::validate_namespace_name(&self.namespace)?;
        crate::registry::validate_namespace_name(&self.name)
            .map_err(|error| format!("agent name: {error}"))?;
        validate_address(&self.address)?;
        if let Some(card) = &self.card_address {
            validate_address(card)?;
        }
        Ok(())
    }

    pub fn view(self, public_origin: &str) -> AgentView {
        let proxy_url = format!(
            "{}/{}/a2a/{}",
            public_origin.trim_end_matches('/'),
            self.namespace,
            self.name
        );
        AgentView {
            name: self.name,
            description: self.description,
            namespace: self.namespace,
            address: self.address,
            card_address: self.card_address,
            proxy_url,
        }
    }
}

pub fn validate_address(address: &str) -> Result<http::Uri, String> {
    let uri: http::Uri = address
        .parse()
        .map_err(|_| "invalid agent URL".to_owned())?;
    if !matches!(uri.scheme_str(), Some("http" | "https"))
        || uri.host().is_none()
        || uri
            .authority()
            .is_none_or(|authority| authority.as_str().contains('@'))
        || address.contains('#')
        || uri
            .authority()
            .zip(uri.host())
            .is_some_and(|(authority, host)| {
                authority.as_str().len() > host.len() && uri.port_u16().is_none()
            })
        || uri.port_u16() == Some(0)
        || uri.path().starts_with("//")
        || praxis_filter::has_dot_dot_traversal(uri.path())
    {
        return Err(
            "agent URL must use HTTP or HTTPS without credentials or a fragment".to_owned(),
        );
    }
    Ok(uri)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn addresses_reject_non_http_credentials_and_fragments() {
        for address in [
            "ftp://host/",
            "file:///tmp/file",
            "/relative",
            "http://user:password@host/rpc",
            "https://host/rpc#fragment",
            "http://host:99999/rpc",
            "http://host:abc/rpc",
            "http://host:0/rpc",
            "https://host//rpc",
            "https://host/../rpc",
            "https://host/%2e%2e/rpc",
        ] {
            assert!(validate_address(address).is_err(), "{address}");
        }
        for address in [
            "https://agent.example/rpc",
            "http://127.0.0.1:9000/rpc",
            "http://[::1]:9000/rpc",
        ] {
            assert!(validate_address(address).is_ok(), "{address}");
        }
    }

    #[test]
    fn entry_defaults_and_view_preserve_namespace_identity() {
        let entry: AgentEntry =
            serde_json::from_str(r#"{"name":"worker","address":"http://backend/rpc"}"#)
                .expect("entry");
        assert_eq!(entry.namespace, "default");
        assert!(entry.validate().is_ok());
        assert_eq!(
            entry.view("https://proxy.example/").proxy_url,
            "https://proxy.example/default/a2a/worker"
        );
    }
}
