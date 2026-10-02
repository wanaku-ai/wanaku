use std::io::Read;
use std::time::Duration;

use http::{Response, StatusCode};
use wanaku_types::http_response::{json_err, json_ok};

use crate::api::PluginCatalogEntry;

pub const CATALOG_URL_ENV: &str = "WANAKU_PLUGIN_CATALOG_URL";
pub const DEFAULT_CATALOG_URL: &str = "https://api.github.com/repos/wanaku-ai/wanaku-barn/releases";
const MAX_DOWNLOAD_BYTES: usize = 16 * 1024 * 1024;
const MAX_MANIFEST_BYTES: u64 = 64 * 1024;

#[derive(serde::Deserialize)]
#[serde(untagged)]
enum CatalogSource {
    Entries(Vec<PluginCatalogEntry>),
    Releases(Vec<Release>),
}

#[derive(serde::Deserialize)]
struct Release {
    assets: Vec<ReleaseAsset>,
    #[serde(default)]
    draft: bool,
    #[serde(default)]
    body: Option<String>,
    author: ReleaseAuthor,
}

#[derive(serde::Deserialize)]
struct ReleaseAuthor {
    login: String,
}

#[derive(serde::Deserialize)]
struct ReleaseAsset {
    name: String,
    browser_download_url: String,
}

async fn download(client: &reqwest::Client, url: &str) -> Result<Vec<u8>, String> {
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
        if bytes.len().saturating_add(chunk.len()) > MAX_DOWNLOAD_BYTES {
            return Err("catalog download exceeds 16 MiB".to_owned());
        }
        bytes.extend_from_slice(&chunk);
    }
    Ok(bytes)
}

fn archive_entry(bytes: &[u8], url: String) -> Result<PluginCatalogEntry, String> {
    let mut archive =
        zip::ZipArchive::new(std::io::Cursor::new(bytes)).map_err(|error| error.to_string())?;
    let index = manifest_index(&mut archive)?;
    let file = archive.by_index(index).map_err(|error| error.to_string())?;
    let mut manifest = Vec::new();
    file.take(MAX_MANIFEST_BYTES + 1)
        .read_to_end(&mut manifest)
        .map_err(|error| error.to_string())?;
    if manifest.len() as u64 > MAX_MANIFEST_BYTES {
        return Err("plugin manifest exceeds 64 KiB".to_owned());
    }
    parse_entry(&manifest, url)
}

fn manifest_index(archive: &mut zip::ZipArchive<std::io::Cursor<&[u8]>>) -> Result<usize, String> {
    let mut manifest_index = None;
    for index in 0..archive.len() {
        let file = archive.by_index(index).map_err(|error| error.to_string())?;
        let Some(path) = file.enclosed_name() else {
            continue;
        };
        if path.file_name().and_then(|name| name.to_str()) != Some("plugin.json") {
            continue;
        }
        if manifest_index.is_none() {
            manifest_index = Some(index);
        }
        if path == std::path::Path::new("plugin.json") {
            manifest_index = Some(index);
            break;
        }
    }
    manifest_index.ok_or_else(|| "plugin archive has no plugin.json".to_owned())
}

fn parse_entry(manifest: &[u8], url: String) -> Result<PluginCatalogEntry, String> {
    let value: serde_json::Value =
        serde_json::from_slice(manifest).map_err(|error| error.to_string())?;
    let mut entry: PluginCatalogEntry =
        serde_json::from_value(value.clone()).map_err(|error| error.to_string())?;
    if let Some(fields) = value.as_object() {
        for (key, value) in fields {
            if !matches!(
                key.as_str(),
                "id" | "name"
                    | "version"
                    | "description"
                    | "publisher"
                    | "license"
                    | "dependencies"
                    | "requires"
                    | "metadata"
                    | "url"
            ) {
                entry.metadata.insert(key.clone(), value.clone());
            }
        }
    }
    entry.url = url;
    Ok(entry)
}

async fn fetch_catalog(
    client: &reqwest::Client,
    url: &str,
) -> Result<Vec<PluginCatalogEntry>, String> {
    let bytes = download(client, url).await?;
    let source: CatalogSource =
        serde_json::from_slice(&bytes).map_err(|error| error.to_string())?;
    let entries = match source {
        CatalogSource::Entries(entries) => entries,
        CatalogSource::Releases(releases) => release_entries(client, releases).await?,
    };
    for entry in &entries {
        validate_entry(entry)?;
    }
    Ok(entries)
}

async fn release_entries(
    client: &reqwest::Client,
    releases: Vec<Release>,
) -> Result<Vec<PluginCatalogEntry>, String> {
    let mut entries = Vec::new();
    for release in releases.into_iter().filter(|release| !release.draft) {
        for asset in release
            .assets
            .into_iter()
            .filter(|asset| asset.name.contains("-plugin-") && asset.name.ends_with(".zip"))
        {
            let bytes = download(client, &asset.browser_download_url).await?;
            let mut entry = archive_entry(&bytes, asset.browser_download_url)?;
            if entry.description.is_empty() {
                entry.description = release.body.clone().unwrap_or_default();
            }
            if entry.publisher.is_empty() {
                entry.publisher.clone_from(&release.author.login);
            }
            entries.push(entry);
        }
    }
    Ok(entries)
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

pub(crate) async fn handle_catalog(client: &reqwest::Client, url: &str) -> Response<Vec<u8>> {
    match tokio::time::timeout(Duration::from_secs(30), fetch_catalog(client, url)).await {
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
    use std::io::Write;

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

    fn archive(manifest: &[u8]) -> Vec<u8> {
        let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
        writer
            .start_file(
                "bundle/plugin.json",
                zip::write::SimpleFileOptions::default(),
            )
            .unwrap();
        writer.write_all(manifest).unwrap();
        writer.finish().unwrap().into_inner()
    }

    #[tokio::test]
    async fn custom_catalog_preserves_metadata_and_requirements() {
        let body = serde_json::json!([{
            "id":"plugin", "name":"Plugin", "version":"1.2.3",
            "description":"Description", "publisher":"Publisher", "license":"Apache-2.0",
            "dependencies":[{"id":"other", "version":">=1"}],
            "requires":{"services":[{"id":"backend", "version":"2"}]},
            "metadata":{"category":"tools"}, "url":"https://example.com/plugin.zip"
        }]);
        let url = serve("200 OK", serde_json::to_vec(&body).unwrap());
        let response = handle_catalog(&reqwest::Client::new(), &url).await;
        assert_eq!(response.status(), StatusCode::OK);
        let response: serde_json::Value = serde_json::from_slice(response.body()).unwrap();
        assert_eq!(response["data"][0]["metadata"]["category"], "tools");
        assert_eq!(
            response["data"][0]["requires"]["services"][0]["id"],
            "backend"
        );
        assert_eq!(response["data"][0]["dependencies"][0]["id"], "other");
    }

    #[tokio::test]
    async fn releases_only_include_plugin_archives_and_use_manifest_version() {
        let manifest = br#"{"id":"barn", "name":"Barn", "version":"0.3.0", "entrypoint":"plugin.js", "requires":{"services":[{"id":"barn-api","version":"1"}]}}"#;
        let archive_url = serve("200 OK", archive(manifest));
        let release = serde_json::json!([{
            "author":{"login":"wanaku"}, "body":"Release description", "assets":[
                {"name":"barn-plugin-0.3.0-SNAPSHOT.zip", "browser_download_url":archive_url},
                {"name":"backend.zip", "browser_download_url":"http://unused.invalid"},
                {"name":"barn-plugin-0.3.0.zip.asc", "browser_download_url":"http://unused.invalid"}
            ]
        }]);
        let url = serve("200 OK", serde_json::to_vec(&release).unwrap());
        let entries = fetch_catalog(&reqwest::Client::new(), &url).await.unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].version, "0.3.0");
        assert_eq!(entries[0].metadata["entrypoint"], "plugin.js");
        assert_eq!(entries[0].url, archive_url);
        assert_eq!(entries[0].publisher, "wanaku");
        assert_eq!(entries[0].description, "Release description");
        assert_eq!(entries[0].requires.services[0].id, "barn-api");
    }

    #[tokio::test]
    async fn unavailable_and_invalid_catalogs_return_bad_gateway() {
        for (status, body) in [
            ("200 OK", br#"[{"id":"../outside","name":"P","version":"1","url":"https://example.com/plugin.zip"}]"#.to_vec()),
            ("503 Service Unavailable", b"unavailable".to_vec()),
            ("200 OK", b"invalid json".to_vec()),
            (
                "200 OK",
                br#"[{"id":"p","name":"P","version":"1","url":"file:///tmp/plugin.zip"}]"#.to_vec(),
            ),
        ] {
            let url = serve(status, body);
            let response = handle_catalog(&reqwest::Client::new(), &url).await;
            assert_eq!(response.status(), StatusCode::BAD_GATEWAY);
        }
    }

    #[tokio::test]
    async fn empty_catalog_is_successful() {
        let url = serve("200 OK", b"[]".to_vec());
        assert!(
            fetch_catalog(&reqwest::Client::new(), &url)
                .await
                .unwrap()
                .is_empty()
        );
    }

    #[test]
    fn root_manifest_takes_priority_over_nested_manifest() {
        let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
        for (path, id) in [("nested/plugin.json", "nested"), ("plugin.json", "root")] {
            writer
                .start_file(path, zip::write::SimpleFileOptions::default())
                .unwrap();
            write!(
                writer,
                "{{\"id\":\"{id}\",\"name\":\"Plugin\",\"version\":\"1\"}}"
            )
            .unwrap();
        }
        let bytes = writer.finish().unwrap().into_inner();
        assert_eq!(
            archive_entry(&bytes, "https://example.com/plugin.zip".to_owned())
                .unwrap()
                .id,
            "root"
        );
    }

    #[test]
    fn oversized_manifest_is_rejected() {
        let bytes = archive(&vec![
            b' ';
            usize::try_from(MAX_MANIFEST_BYTES + 1).unwrap()
        ]);
        assert!(archive_entry(&bytes, "https://example.com/plugin.zip".to_owned()).is_err());
    }
}
