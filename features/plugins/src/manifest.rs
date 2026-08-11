#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PluginManifest {
    pub id: String,
    pub name: String,
    pub version: String,
    pub entrypoint: String,
    #[serde(default)]
    pub styles: Vec<String>,
    #[serde(default)]
    pub requires: PluginRequires,
    #[serde(default)]
    pub permissions: Vec<String>,
}

#[derive(Debug, Clone, Default, serde::Serialize, serde::Deserialize)]
pub struct PluginRequires {
    #[serde(rename = "hostApi", default)]
    pub host_api: String,
    #[serde(default)]
    pub services: Vec<ServiceRequirement>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct ServiceRequirement {
    pub id: String,
    pub version: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deserialize_full_manifest() {
        let json = r#"{
            "id": "my-plugin",
            "name": "My Plugin",
            "version": "1.0.0",
            "entrypoint": "index.js",
            "styles": ["style.css"],
            "requires": {
                "hostApi": "1.0",
                "services": [{"id": "chat", "version": "1.0"}]
            },
            "permissions": ["tools:read"]
        }"#;
        let result = serde_json::from_str::<PluginManifest>(json);
        assert!(result.is_ok(), "failed to deserialize valid manifest");
        if let Ok(manifest) = result {
            assert_eq!(manifest.id, "my-plugin");
            assert_eq!(manifest.name, "My Plugin");
            assert_eq!(manifest.version, "1.0.0");
            assert_eq!(manifest.entrypoint, "index.js");
            assert_eq!(manifest.styles.len(), 1);
            assert_eq!(manifest.requires.host_api, "1.0");
            assert_eq!(manifest.requires.services.len(), 1);
            assert_eq!(manifest.permissions.len(), 1);
        }
    }

    #[test]
    fn deserialize_minimal_manifest() {
        let json = r#"{
            "id": "minimal",
            "name": "Minimal Plugin",
            "version": "0.1.0",
            "entrypoint": "main.js"
        }"#;
        let result = serde_json::from_str::<PluginManifest>(json);
        assert!(result.is_ok(), "failed to deserialize minimal manifest");
        if let Ok(manifest) = result {
            assert_eq!(manifest.id, "minimal");
            assert!(manifest.styles.is_empty());
            assert_eq!(manifest.requires.host_api, "");
            assert!(manifest.requires.services.is_empty());
            assert!(manifest.permissions.is_empty());
        }
    }

    #[test]
    fn deserialize_missing_required_field() {
        let json = r#"{"id": "no-name", "version": "1.0.0", "entrypoint": "main.js"}"#;
        let result = serde_json::from_str::<PluginManifest>(json);
        assert!(result.is_err());
    }
}
