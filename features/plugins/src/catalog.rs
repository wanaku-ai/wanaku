use std::collections::HashSet;
use std::path::PathBuf;
use std::time::Duration;

use http::{Response, StatusCode};
use tokio::io::AsyncReadExt;
use wanaku_types::http_response::{json_err, json_ok};

use crate::api::PluginCatalogEntry;

pub const CATALOG_PATH_ENV: &str = "WANAKU_PLUGIN_CATALOG_PATH";
pub const CATALOG_URL_ENV: &str = "WANAKU_PLUGIN_CATALOG_URL";
const EMBEDDED_CATALOG: &[u8] = include_bytes!("../plugin-catalog.json");
const MAX_CATALOG_BYTES: usize = 1024 * 1024;

pub(crate) enum CatalogSource {
    Embedded,
    Local(PathBuf),
    Remote(String),
}

impl CatalogSource {
    pub(crate) fn from_config() -> Self {
        match std::env::var_os(CATALOG_PATH_ENV) {
            Some(path) => Self::Local(PathBuf::from(path)),
            None => match std::env::var(CATALOG_URL_ENV) {
                Ok(url) => Self::Remote(url),
                Err(_) => Self::Embedded,
            },
        }
    }
}

async fn download(client: &reqwest::Client, url: &str) -> Result<Vec<u8>, String> {
    let url = reqwest::Url::parse(url).map_err(|error| error.to_string())?;
    if !matches!(url.scheme(), "http" | "https") {
        return Err("catalog source URL must use HTTP or HTTPS".to_owned());
    }
    let mut response = client
        .get(url)
        .header(reqwest::header::USER_AGENT, "wanaku-plugin-catalog")
        .timeout(Duration::from_secs(15))
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|error| error.to_string())?;
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|error| error.to_string())? {
        if bytes.len().saturating_add(chunk.len()) > MAX_CATALOG_BYTES {
            return Err("plugin catalog exceeds 1 MiB".to_owned());
        }
        bytes.extend_from_slice(&chunk);
    }
    Ok(bytes)
}

fn parse_catalog(bytes: &[u8]) -> Result<Vec<PluginCatalogEntry>, String> {
    if bytes.len() > MAX_CATALOG_BYTES {
        return Err("plugin catalog exceeds 1 MiB".to_owned());
    }
    let entries: Vec<PluginCatalogEntry> =
        serde_json::from_slice(bytes).map_err(|error| error.to_string())?;
    let mut ids = HashSet::new();
    for entry in &entries {
        validate_entry(entry)?;
        if !ids.insert(&entry.id) {
            return Err("catalog must contain one entry per plugin id".to_owned());
        }
    }
    Ok(entries)
}

async fn fetch_catalog(
    client: &reqwest::Client,
    source: &CatalogSource,
) -> Result<Vec<PluginCatalogEntry>, String> {
    match source {
        CatalogSource::Embedded => parse_catalog(EMBEDDED_CATALOG),
        CatalogSource::Local(path) => {
            let file = tokio::fs::File::open(path)
                .await
                .map_err(|error| error.to_string())?;
            let mut bytes = Vec::new();
            file.take((MAX_CATALOG_BYTES + 1) as u64)
                .read_to_end(&mut bytes)
                .await
                .map_err(|error| error.to_string())?;
            parse_catalog(&bytes)
        }
        CatalogSource::Remote(url) => parse_catalog(&download(client, url).await?),
    }
}

fn validate_entry(entry: &PluginCatalogEntry) -> Result<(), String> {
    if !crate::routes::is_valid_segment(&entry.id) {
        return Err("catalog plugin id must be a single path segment".to_owned());
    }
    if entry.id.trim().is_empty() || entry.name.trim().is_empty() || entry.version.trim().is_empty()
    {
        return Err("catalog entry is missing id, name, or version".to_owned());
    }
    let url = reqwest::Url::parse(&entry.url).map_err(|error| error.to_string())?;
    if !matches!(url.scheme(), "http" | "https") {
        return Err("catalog download URL must use HTTP or HTTPS".to_owned());
    }
    Ok(())
}

pub(crate) async fn handle_catalog(
    client: &reqwest::Client,
    source: &CatalogSource,
) -> Response<Vec<u8>> {
    match tokio::time::timeout(Duration::from_secs(30), fetch_catalog(client, source)).await {
        Ok(Ok(entries)) => match serde_json::to_value(entries) {
            Ok(value) => json_ok(&value),
            Err(error) => json_err(StatusCode::INTERNAL_SERVER_ERROR, &error.to_string()),
        },
        Ok(Err(error)) => {
            tracing::warn!(%error, "failed to load plugin catalog");
            json_err(StatusCode::BAD_GATEWAY, "failed to load plugin catalog")
        }
        Err(_) => json_err(StatusCode::BAD_GATEWAY, "plugin catalog request timed out"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};

    fn serve(status: &str, body: Vec<u8>) -> String {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let status = status.to_owned();
        tokio::task::spawn_blocking(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0; 4096];
            assert!(stream.read(&mut request).unwrap() > 0);
            write!(
                stream,
                "HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                body.len()
            )
            .unwrap();
            stream.write_all(&body).unwrap();
        });
        url
    }

    fn independent_catalog() -> Vec<u8> {
        serde_json::to_vec(&serde_json::json!([
            {
                "id":"service-tools", "name":"Service Tools", "version":"1.2.3",
                "description":"Manage services", "publisher":"Independent Publisher", "license":"Apache-2.0",
                "dependencies":[{"id":"other", "version":">=1"}],
                "requires":{"hostApi":">=1.0 <2.0", "services":[{"id":"backend", "version":"2"}]},
                "metadata":{"category":"tools", "homepage":"https://publisher.example/tools"},
                "url":"https://archives.invalid/tools.zip"
            },
            {
                "id":"reports", "name":"Reports", "version":"2.0.0",
                "publisher":"Another Publisher", "url":"https://another.invalid/reports.zip"
            }
        ])).unwrap()
    }

    #[tokio::test]
    async fn embedded_catalog_has_curated_barn_metadata() {
        let entries = fetch_catalog(&reqwest::Client::new(), &CatalogSource::Embedded)
            .await
            .unwrap();
        assert_eq!(entries.len(), 2);
        let barn = entries
            .iter()
            .find(|entry| entry.id == "wanaku-barn")
            .expect("Barn catalog entry");
        assert_eq!(barn.id, "wanaku-barn");
        assert_eq!(barn.version, "0.3.0");
        assert_eq!(barn.publisher, "Wanaku");
        assert_eq!(barn.license, "Apache-2.0");
        assert_eq!(barn.requires.host_api, ">=1.0 <2.0");
        assert_eq!(barn.requires.services[0].id, "wanaku-barn-api");
        assert!(barn.description.len() < 200);
        assert!(barn.url.ends_with("wanaku-barn-plugin-0.3.0-SNAPSHOT.zip"));
    }

    #[tokio::test]
    async fn embedded_catalog_has_curated_debugger_metadata() {
        let entries = fetch_catalog(&reqwest::Client::new(), &CatalogSource::Embedded)
            .await
            .unwrap();
        let debugger = entries
            .iter()
            .find(|entry| entry.id == "wanaku-debugger")
            .expect("Debugger catalog entry");
        assert_eq!(debugger.name, "Deployment Debugger");
        assert_eq!(debugger.version, "0.3.0");
        assert_eq!(debugger.publisher, "Wanaku");
        assert_eq!(debugger.license, "Apache-2.0");
        assert_eq!(debugger.requires.host_api, ">=1.0 <2.0");
        assert!(debugger.requires.services.is_empty());
        assert!(debugger.dependencies.is_empty());
        assert!(debugger.description.len() < 200);
        assert_eq!(
            debugger.url,
            "https://github.com/wanaku-ai/wanaku-barn/releases/download/early-access/wanaku-debugger-plugin-0.3.0-SNAPSHOT.zip"
        );
    }

    #[tokio::test]
    async fn local_and_remote_catalogs_support_independent_publishers_without_archive_downloads() {
        let file = tempfile::NamedTempFile::new().unwrap();
        std::fs::write(file.path(), independent_catalog()).unwrap();
        let url = serve("200 OK", independent_catalog());
        for source in [
            CatalogSource::Local(file.path().to_owned()),
            CatalogSource::Remote(url),
        ] {
            let response = handle_catalog(&reqwest::Client::new(), &source).await;
            assert_eq!(response.status(), StatusCode::OK);
            let response: serde_json::Value = serde_json::from_slice(response.body()).unwrap();
            let entries = response["data"].as_array().unwrap();
            assert_eq!(entries.len(), 2);
            assert_eq!(entries[0]["publisher"], "Independent Publisher");
            assert_eq!(entries[1]["publisher"], "Another Publisher");
            assert_eq!(entries[0]["metadata"]["category"], "tools");
            assert_eq!(entries[0]["requires"]["services"][0]["id"], "backend");
            assert_eq!(entries[0]["dependencies"][0]["id"], "other");
        }
    }

    #[tokio::test]
    async fn invalid_catalogs_are_rejected_for_local_and_remote_sources() {
        for body in [
            br#"[{"id":"../outside","name":"P","version":"1","url":"https://example.com/plugin.zip"}]"#.to_vec(),
            b"invalid json".to_vec(),
            br#"[{"id":"p","name":"P","version":"1","url":"file:///tmp/plugin.zip"}]"#.to_vec(),
            br#"[{"id":"p","name":" ","version":"1","url":"https://example.com/plugin.zip"}]"#.to_vec(),
            br#"[{"author":{"login":"wanaku"},"body":"Release notes","assets":[]}]"#.to_vec(),
            vec![b' '; MAX_CATALOG_BYTES + 1],
        ] {
            let file = tempfile::NamedTempFile::new().unwrap();
            std::fs::write(file.path(), &body).unwrap();
            let url = serve("200 OK", body);
            for source in [CatalogSource::Local(file.path().to_owned()), CatalogSource::Remote(url)] {
                let response = handle_catalog(&reqwest::Client::new(), &source).await;
                assert_eq!(response.status(), StatusCode::BAD_GATEWAY);
            }
        }
    }

    #[tokio::test]
    async fn local_catalog_updates_are_visible_on_the_next_request() {
        let file = tempfile::NamedTempFile::new().unwrap();
        std::fs::write(file.path(), independent_catalog()).unwrap();
        let source = CatalogSource::Local(file.path().to_owned());
        let client = reqwest::Client::new();
        assert_eq!(fetch_catalog(&client, &source).await.unwrap().len(), 2);
        std::fs::write(file.path(), b"[]").unwrap();
        assert!(fetch_catalog(&client, &source).await.unwrap().is_empty());
    }

    #[tokio::test]
    async fn unavailable_sources_return_bad_gateway_without_default_fallback() {
        let directory = tempfile::tempdir().unwrap();
        let url = serve("503 Service Unavailable", b"unavailable".to_vec());
        for source in [
            CatalogSource::Local(directory.path().join("missing.json")),
            CatalogSource::Remote(url),
        ] {
            let response = handle_catalog(&reqwest::Client::new(), &source).await;
            assert_eq!(response.status(), StatusCode::BAD_GATEWAY);
        }
    }

    #[test]
    fn duplicate_plugin_ids_are_rejected() {
        let mut entries: serde_json::Value =
            serde_json::from_slice(&independent_catalog()).unwrap();
        entries[1]["id"] = entries[0]["id"].clone();
        assert!(parse_catalog(&serde_json::to_vec(&entries).unwrap()).is_err());
    }

    #[test]
    fn empty_catalog_is_successful() {
        assert!(parse_catalog(b"[]").unwrap().is_empty());
    }
}
