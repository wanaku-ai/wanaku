use std::io::Read;
use std::path::{Component, Path};

use crate::manifest::PluginManifest;

#[derive(Debug, thiserror::Error)]
pub enum InstallError {
    #[error("plugins directory is not configured on the server")]
    PluginsPathNotConfigured,
    #[error("failed to download plugin archive: {0}")]
    DownloadFailed(String),
    #[error("invalid zip archive: {0}")]
    InvalidArchive(String),
    #[error("insecure archive entry path: {0}")]
    InsecurePath(String),
    #[error("io error during extraction: {0}")]
    Io(#[from] std::io::Error),
    #[error("failed to parse extracted plugin manifest: {0}")]
    ManifestError(String),
    #[error("plugin manifest missing required fields (id, entrypoint)")]
    InvalidManifest,
    #[error("installed plugin id '{0}' does not match requested id '{1}'")]
    IdMismatch(String, String),
}

pub async fn install_plugin_from_url(
    client: &reqwest::Client,
    plugins_path: &Path,
    plugin_id: &str,
    _version: &str,
    url: &str,
) -> Result<PluginManifest, InstallError> {
    let response = client
        .get(url)
        .send()
        .await
        .map_err(|e| InstallError::DownloadFailed(e.to_string()))?;

    if !response.status().is_success() {
        return Err(InstallError::DownloadFailed(format!(
            "server responded with status {}",
            response.status()
        )));
    }

    let bytes = response
        .bytes()
        .await
        .map_err(|e| InstallError::DownloadFailed(e.to_string()))?;

    extract_plugin_archive(&bytes, plugins_path, plugin_id)
}

pub fn extract_plugin_archive(
    archive_bytes: &[u8],
    plugins_path: &Path,
    plugin_id: &str,
) -> Result<PluginManifest, InstallError> {
    let reader = std::io::Cursor::new(archive_bytes);
    let mut zip = zip::ZipArchive::new(reader)
        .map_err(|e| InstallError::InvalidArchive(e.to_string()))?;

    // Find the prefix containing plugin.json (either root or a single root subdirectory like `wanaku-barn-plugin-0.3.0/`)
    let mut manifest_prefix: Option<std::path::PathBuf> = None;
    for i in 0..zip.len() {
        let file = zip
            .by_index(i)
            .map_err(|e| InstallError::InvalidArchive(e.to_string()))?;
        let file_name = file.name().to_owned();
        let Some(enclosed_path) = file.enclosed_name() else {
            return Err(InstallError::InsecurePath(file_name));
        };
        let enclosed_path = enclosed_path.to_path_buf();
        if has_parent_components(&enclosed_path) {
            return Err(InstallError::InsecurePath(file_name));
        }

        if enclosed_path.file_name().and_then(|n| n.to_str()) == Some("plugin.json") {
            let parent = enclosed_path.parent().map(std::path::Path::to_path_buf).unwrap_or_default();
            // Prefer root plugin.json if present, or first found subdirectory plugin.json
            if parent.as_os_str().is_empty() || manifest_prefix.is_none() {
                manifest_prefix = Some(parent);
                if manifest_prefix.as_ref().is_some_and(|p| p.as_os_str().is_empty()) {
                    break;
                }
            }
        }
    }

    let manifest_prefix = manifest_prefix.ok_or_else(|| {
        InstallError::ManifestError("missing plugin.json in archive".to_owned())
    })?;

    let target_dir = plugins_path.join(plugin_id);
    std::fs::create_dir_all(&target_dir)?;

    for i in 0..zip.len() {
        let mut file = zip
            .by_index(i)
            .map_err(|e| InstallError::InvalidArchive(e.to_string()))?;

        let file_name = file.name().to_owned();
        let Some(enclosed_path) = file.enclosed_name() else {
            return Err(InstallError::InsecurePath(file_name));
        };
        let enclosed_path = enclosed_path.to_path_buf();

        if has_parent_components(&enclosed_path) {
            return Err(InstallError::InsecurePath(file_name));
        }

        // Strip the root prefix so files are extracted directly into target_dir
        let Ok(relative_path) = enclosed_path.strip_prefix(&manifest_prefix) else {
            continue;
        };

        if relative_path.as_os_str().is_empty() {
            continue;
        }

        let outpath = target_dir.join(relative_path);

        if file.is_dir() {
            std::fs::create_dir_all(&outpath)?;
        } else {
            if let Some(p) = outpath.parent() {
                if !p.exists() {
                    std::fs::create_dir_all(p)?;
                }
            }
            let mut outfile = std::fs::File::create(&outpath)?;
            let mut buf = Vec::new();
            file.read_to_end(&mut buf)?;
            use std::io::Write;
            outfile.write_all(&buf)?;
            outfile.sync_all()?;
        }
    }

    // Verify and parse plugin.json
    let manifest_path = target_dir.join("plugin.json");
    let manifest_content = std::fs::read_to_string(&manifest_path).map_err(|e| {
        InstallError::ManifestError(format!("missing plugin.json in extracted directory: {e}"))
    })?;

    let manifest: PluginManifest = serde_json::from_str(&manifest_content)
        .map_err(|e| InstallError::ManifestError(format!("invalid plugin.json: {e}")))?;

    if manifest.id.is_empty() || manifest.entrypoint.is_empty() {
        return Err(InstallError::InvalidManifest);
    }

    if manifest.id != plugin_id {
        return Err(InstallError::IdMismatch(manifest.id, plugin_id.to_owned()));
    }

    Ok(manifest)
}

fn has_parent_components(path: &Path) -> bool {
    path.components().any(|c| matches!(c, Component::ParentDir | Component::RootDir | Component::Prefix(_)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use zip::write::SimpleFileOptions;

    fn create_test_zip(entries: &[(&str, &[u8])]) -> Vec<u8> {
        let mut buf = Vec::new();
        {
            let mut zip = zip::ZipWriter::new(std::io::Cursor::new(&mut buf));
            let options = SimpleFileOptions::default();
            for (name, content) in entries {
                zip.start_file(*name, options).unwrap();
                zip.write_all(content).unwrap();
            }
            zip.finish().unwrap();
        }
        buf
    }

    #[test]
    fn extract_valid_plugin() {
        let manifest = r#"{
            "id": "test-plugin",
            "name": "Test Plugin",
            "version": "1.0.0",
            "entrypoint": "main.js"
        }"#;

        let zip_bytes = create_test_zip(&[
            ("plugin.json", manifest.as_bytes()),
            ("main.js", b"console.log('hello');"),
        ]);

        let dir = tempfile::tempdir().unwrap();
        let extracted = extract_plugin_archive(&zip_bytes, dir.path(), "test-plugin").unwrap();

        assert_eq!(extracted.id, "test-plugin");
        assert_eq!(extracted.name, "Test Plugin");
        assert!(dir.path().join("test-plugin").join("main.js").exists());
    }

    #[test]
    fn extract_rejects_zip_slip() {
        let manifest = r#"{
            "id": "bad-plugin",
            "name": "Bad Plugin",
            "version": "1.0.0",
            "entrypoint": "main.js"
        }"#;

        let mut buf = Vec::new();
        {
            let mut zip = zip::ZipWriter::new(std::io::Cursor::new(&mut buf));
            let options = SimpleFileOptions::default();
            zip.start_file("../outside.txt", options).unwrap();
            zip.write_all(b"malicious").unwrap();
            zip.start_file("plugin.json", options).unwrap();
            zip.write_all(manifest.as_bytes()).unwrap();
            zip.finish().unwrap();
        }

        let dir = tempfile::tempdir().unwrap();
        let res = extract_plugin_archive(&buf, dir.path(), "bad-plugin");
        assert!(res.is_err());
    }

    #[test]
    fn extract_nested_plugin_directory() {
        let manifest = r#"{
            "id": "wanaku-barn",
            "name": "Wanaku Barn",
            "version": "0.3.0",
            "entrypoint": "plugin.js",
            "styles": ["plugin.css"]
        }"#;

        let zip_bytes = create_test_zip(&[
            ("wanaku-barn-plugin-0.3.0-SNAPSHOT/plugin.json", manifest.as_bytes()),
            ("wanaku-barn-plugin-0.3.0-SNAPSHOT/plugin.js", b"console.log('barn');"),
            ("wanaku-barn-plugin-0.3.0-SNAPSHOT/plugin.css", b"body { color: red; }"),
        ]);

        let dir = tempfile::tempdir().unwrap();
        let extracted = extract_plugin_archive(&zip_bytes, dir.path(), "wanaku-barn").unwrap();

        assert_eq!(extracted.id, "wanaku-barn");
        assert!(dir.path().join("wanaku-barn").join("plugin.json").exists());
        assert!(dir.path().join("wanaku-barn").join("plugin.js").exists());
        assert!(dir.path().join("wanaku-barn").join("plugin.css").exists());
    }

    #[test]
    fn extract_rejects_mismatched_id() {
        let manifest = r#"{
            "id": "actual-id",
            "name": "Test",
            "version": "1.0.0",
            "entrypoint": "main.js"
        }"#;

        let zip_bytes = create_test_zip(&[("plugin.json", manifest.as_bytes())]);

        let dir = tempfile::tempdir().unwrap();
        let res = extract_plugin_archive(&zip_bytes, dir.path(), "expected-id");
        assert!(matches!(res, Err(InstallError::IdMismatch(_, _))));
    }
}
