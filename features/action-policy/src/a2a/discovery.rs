//! Advertise only bindings that the selected upstream can serve.
use praxis_filter::FilterError;
use serde_json::{Map, Value};

#[derive(Clone, Copy, PartialEq, Eq)]
enum CardVersion {
    Legacy,
    V1,
}

fn declared_version(value: Option<&Value>) -> Result<CardVersion, FilterError> {
    match value {
        None => Ok(CardVersion::V1),
        Some(Value::String(version)) if matches!(version.as_str(), "0.3" | "0.3.0") => {
            Ok(CardVersion::Legacy)
        }
        Some(Value::String(version)) if matches!(version.as_str(), "1.0" | "1.0.0") => {
            Ok(CardVersion::V1)
        }
        _ => Err("upstream agent card has an unsupported protocol version".into()),
    }
}

pub(super) fn rewrite_card(
    value: &mut Value,
    public_url: &str,
    upstream_url: Option<&str>,
) -> Result<(), FilterError> {
    let card = value
        .as_object_mut()
        .ok_or_else(|| FilterError::from("upstream agent card must be an object"))?;
    let fallback = declared_version(card.get("protocolVersion"))?;
    let interfaces = proxy_interfaces(card, public_url, upstream_url, fallback)?;
    let legacy = !card.contains_key("supportedInterfaces") && fallback == CardVersion::Legacy;
    if legacy {
        card.insert(
            "protocolVersion".to_owned(),
            Value::String("0.3.0".to_owned()),
        );
        card.insert("url".to_owned(), Value::String(public_url.to_owned()));
        card.insert(
            "preferredTransport".to_owned(),
            Value::String("JSONRPC".to_owned()),
        );
        card.insert(
            "supportsAuthenticatedExtendedCard".to_owned(),
            Value::Bool(false),
        );
        card.remove("supportedInterfaces");
    } else {
        card.insert("supportedInterfaces".to_owned(), Value::Array(interfaces));
        for field in [
            "protocolVersion",
            "url",
            "preferredTransport",
            "supportsAuthenticatedExtendedCard",
        ] {
            card.remove(field);
        }
    }
    card.remove("additionalInterfaces");
    card.remove("signatures");
    let capabilities = card
        .entry("capabilities")
        .or_insert_with(|| serde_json::json!({}));
    let capabilities = capabilities
        .as_object_mut()
        .ok_or_else(|| FilterError::from("invalid agent card capabilities"))?;
    capabilities.insert("streaming".to_owned(), Value::Bool(false));
    capabilities.insert("pushNotifications".to_owned(), Value::Bool(false));
    if !legacy || capabilities.contains_key("extendedAgentCard") {
        capabilities.insert("extendedAgentCard".to_owned(), Value::Bool(false));
    }
    Ok(())
}

fn proxy_interfaces(
    card: &Map<String, Value>,
    public_url: &str,
    upstream_url: Option<&str>,
    fallback: CardVersion,
) -> Result<Vec<Value>, FilterError> {
    let Some(interfaces) = card.get("supportedInterfaces") else {
        if card
            .get("preferredTransport")
            .is_some_and(|binding| binding != "JSONRPC")
        {
            return Err("upstream agent card does not advertise JSON-RPC".into());
        }
        return Ok(vec![proxy_interface(public_url, fallback, None)]);
    };
    let interfaces = interfaces
        .as_array()
        .ok_or_else(|| FilterError::from("invalid agent card supportedInterfaces"))?;
    let selected_url = upstream_url
        .ok_or_else(|| FilterError::from("selected A2A upstream endpoint is unavailable"))?;
    let mut selected = Vec::new();
    for interface in interfaces {
        if interface["protocolBinding"] != "JSONRPC"
            || !interface
                .get("url")
                .and_then(Value::as_str)
                .zip(Some(selected_url))
                .is_some_and(|(url, selected)| same_endpoint(url, selected))
        {
            continue;
        }
        let url = interface
            .get("url")
            .and_then(Value::as_str)
            .ok_or_else(|| FilterError::from("agent interface URL is required"))?;
        let uri: http::Uri = url
            .parse()
            .map_err(|_| FilterError::from("invalid agent interface URL"))?;
        if !matches!(uri.scheme_str(), Some("http" | "https")) || uri.authority().is_none() {
            return Err("agent interface must use an HTTP(S) URL".into());
        }
        let version = match interface.get("protocolVersion") {
            Some(version) => declared_version(Some(version))?,
            None => fallback,
        };
        let tenant = interface.get("tenant");
        if tenant.is_some_and(|tenant| !tenant.is_string()) {
            return Err("invalid agent interface tenant".into());
        }
        selected.push(proxy_interface(public_url, version, tenant));
    }
    if selected.is_empty() {
        return Err(
            "upstream agent card has no compatible JSON-RPC interface at the configured endpoint"
                .into(),
        );
    }
    Ok(selected)
}

fn same_endpoint(left: &str, right: &str) -> bool {
    let (Ok(left), Ok(right)) = (left.parse::<http::Uri>(), right.parse::<http::Uri>()) else {
        return false;
    };
    let port = |uri: &http::Uri| {
        uri.port_u16().or(match uri.scheme_str() {
            Some("http") => Some(80),
            Some("https") => Some(443),
            _ => None,
        })
    };
    left.scheme_str() == right.scheme_str()
        && left.host().map(str::to_ascii_lowercase) == right.host().map(str::to_ascii_lowercase)
        && port(&left) == port(&right)
        && left.path() == right.path()
        && left.query() == right.query()
}

fn proxy_interface(public_url: &str, version: CardVersion, tenant: Option<&Value>) -> Value {
    let mut interface = serde_json::json!({
        "url": public_url,
        "protocolBinding": "JSONRPC",
        "protocolVersion": if version == CardVersion::Legacy { "0.3" } else { "1.0" },
    });
    if let Some(tenant) = tenant {
        interface["tenant"] = tenant.clone();
    }
    interface
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn v1_discovery_filters_bindings_and_endpoints_without_losing_tenant() {
        let mut card = serde_json::json!({"supportedInterfaces": [
            {"url":"http://backend/rpc", "protocolBinding":"JSONRPC", "protocolVersion":"1.0", "tenant":"blue"},
            {"url":"http://backend/rest", "protocolBinding":"HTTP+JSON", "protocolVersion":"1.0"},
            {"url":"http://other/rpc", "protocolBinding":"JSONRPC", "protocolVersion":"1.0"}
        ], "capabilities":{"streaming":true,"pushNotifications":true,"extendedAgentCard":true}, "signatures":[{}]});
        rewrite_card(
            &mut card,
            "https://proxy/blue/a2a/worker",
            Some("http://backend/rpc"),
        )
        .expect("card");
        assert_eq!(
            card["supportedInterfaces"],
            serde_json::json!([{
                "url":"https://proxy/blue/a2a/worker", "protocolBinding":"JSONRPC", "protocolVersion":"1.0", "tenant":"blue"
            }])
        );
        assert_eq!(card["capabilities"]["extendedAgentCard"], false);
        assert!(card.get("url").is_none());
        assert!(card.get("signatures").is_none());
    }

    #[test]
    fn missing_protocol_information_uses_v1_interface() {
        let mut card =
            serde_json::json!({"name":"Camel", "url":"http://backend/", "capabilities":{}});
        rewrite_card(&mut card, "https://proxy/", None).expect("fallback");
        assert_eq!(card["supportedInterfaces"][0]["protocolVersion"], "1.0");
        assert_eq!(card["supportedInterfaces"][0]["url"], "https://proxy/");
        assert!(card.get("protocolVersion").is_none());
    }

    #[test]
    fn incompatible_interfaces_and_explicit_versions_are_rejected() {
        for fixture in [
            serde_json::json!({"protocolVersion":"2.0"}),
            serde_json::json!({"preferredTransport":"HTTP+JSON"}),
            serde_json::json!({"supportedInterfaces":[]}),
            serde_json::json!({"supportedInterfaces":[{"url":"http://backend/rpc","protocolBinding":"JSONRPC","protocolVersion":"2.0"}]}),
            serde_json::json!({"supportedInterfaces":[{"url":"http://other/rpc","protocolBinding":"JSONRPC","protocolVersion":"1.0"}]}),
        ] {
            let mut card = fixture;
            assert!(rewrite_card(&mut card, "https://proxy/", Some("http://backend/rpc")).is_err());
        }
    }

    #[test]
    fn endpoint_matching_normalizes_default_ports_but_preserves_paths_and_queries() {
        for (configured, advertised) in [
            ("http://backend", "http://backend/"),
            ("http://backend/rpc", "http://BACKEND:80/rpc"),
            (
                "https://backend/rpc?tenant=blue",
                "https://backend:443/rpc?tenant=blue",
            ),
        ] {
            let mut card = serde_json::json!({"supportedInterfaces":[{
                "url":advertised,"protocolBinding":"JSONRPC","protocolVersion":"1.0"
            }]});
            assert!(rewrite_card(&mut card, "https://proxy/", Some(configured)).is_ok());
        }
        for advertised in [
            "http://backend/rpc/",
            "http://backend/rpc?tenant=blue",
            "https://backend/rpc",
            "http://backend:9000/rpc",
        ] {
            assert!(!same_endpoint("http://backend/rpc", advertised));
        }
    }

    #[test]
    fn modern_legacy_interface_retains_shape_and_tenant() {
        let mut card = serde_json::json!({"supportedInterfaces":[{
            "url":"http://backend/rpc","protocolBinding":"JSONRPC","protocolVersion":"0.3", "tenant":"blue"
        }], "capabilities":{"extendedAgentCard":true}});
        rewrite_card(&mut card, "https://proxy/", Some("http://backend/rpc"))
            .expect("modern legacy card");
        assert_eq!(card["supportedInterfaces"][0]["protocolVersion"], "0.3");
        assert_eq!(card["supportedInterfaces"][0]["tenant"], "blue");
        assert_eq!(card["capabilities"]["extendedAgentCard"], false);
        assert!(card.get("url").is_none());
    }

    #[test]
    fn static_discovery_does_not_advertise_a_second_upstream_endpoint() {
        let mut card = serde_json::json!({"supportedInterfaces":[
            {"url":"http://first/rpc","protocolBinding":"JSONRPC","protocolVersion":"1.0.0"},
            {"url":"http://other/rpc","protocolBinding":"JSONRPC","protocolVersion":"0.3"}
        ]});
        assert!(rewrite_card(&mut card, "https://proxy/", None).is_err());
        assert!(rewrite_card(&mut card, "https://proxy/", Some("http://first/")).is_err());
        rewrite_card(&mut card, "https://proxy/", Some("http://first/rpc")).expect("selected card");
        assert_eq!(
            card["supportedInterfaces"]
                .as_array()
                .expect("interfaces")
                .len(),
            1
        );
        assert_eq!(card["supportedInterfaces"][0]["protocolVersion"], "1.0");
    }
}
