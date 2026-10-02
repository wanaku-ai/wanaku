use praxis_core::connectivity::{ConnectionOptions, Upstream};
use std::sync::Arc;
use wanaku_types::agents::{AgentEntry, validate_address};

#[derive(Debug, Clone)]
pub struct AgentEndpoint {
    pub upstream: Upstream,
    pub path: String,
}

#[derive(Debug, Clone)]
pub(crate) struct RegisteredAgent {
    pub entry: AgentEntry,
    pub rpc: AgentEndpoint,
    pub card: AgentEndpoint,
}

impl RegisteredAgent {
    pub fn new(entry: AgentEntry) -> Result<Self, String> {
        entry.validate()?;
        let rpc = endpoint(&entry.address)?;
        let uri = validate_address(&entry.address)?;
        let card_address = entry.card_address.clone().unwrap_or_else(|| {
            format!(
                "{}://{}/.well-known/agent-card.json",
                uri.scheme_str().unwrap_or("http"),
                uri.authority().map_or("", http::uri::Authority::as_str)
            )
        });
        let card = endpoint(&card_address)?;
        Ok(Self { entry, rpc, card })
    }
}

fn endpoint(address: &str) -> Result<AgentEndpoint, String> {
    let uri = validate_address(address)?;
    let host = uri.host().ok_or("agent URL requires a host")?;
    let secure = uri.scheme_str() == Some("https");
    let port = uri.port_u16().unwrap_or(if secure { 443 } else { 80 });
    let tls = secure
        .then(|| {
            praxis_tls::CachedClusterTls::try_from_config(&praxis_tls::ClusterTls {
                sni: Some(host.trim_matches(['[', ']']).to_owned()),
                ..praxis_tls::ClusterTls::default()
            })
        })
        .transpose()
        .map_err(|error| error.to_string())?;
    let authority = uri
        .authority()
        .ok_or("agent URL requires an authority")?
        .as_str();
    Ok(AgentEndpoint {
        upstream: Upstream {
            address: Arc::from(format!("{host}:{port}")),
            authority: Some(
                http::HeaderValue::from_str(authority).map_err(|error| error.to_string())?,
            ),
            connection: Arc::new(ConnectionOptions::default()),
            tls,
        },
        path: uri
            .path_and_query()
            .map_or("/", http::uri::PathAndQuery::as_str)
            .to_owned(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn endpoints_preserve_path_authority_and_verified_tls() {
        let secure = endpoint("https://agent.example:9443/rpc?tenant=blue").expect("endpoint");
        assert_eq!(&*secure.upstream.address, "agent.example:9443");
        assert_eq!(secure.path, "/rpc?tenant=blue");
        assert_eq!(
            secure.upstream.authority.expect("authority"),
            "agent.example:9443"
        );
        let tls = secure.upstream.tls.expect("TLS");
        assert_eq!(tls.sni(), Some("agent.example"));
        assert!(tls.verify());
        let local = endpoint("http://[::1]:9000/rpc").expect("IPv6 endpoint");
        assert_eq!(&*local.upstream.address, "[::1]:9000");
    }
}
