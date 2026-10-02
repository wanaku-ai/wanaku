use std::collections::HashMap;

use crate::manifest::PluginManifest;

#[derive(Debug, Clone, serde::Deserialize, serde::Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct InstallPluginRequest {
    pub id: String,
    pub version: String,
    pub url: String,
}

#[derive(Debug, Clone, serde::Deserialize, serde::Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct PluginServiceTarget {
    pub target: String,
}

#[derive(Debug, Clone, serde::Deserialize, serde::Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct ConfigurePluginRequest {
    pub services: HashMap<String, PluginServiceTarget>,
}

#[derive(Debug, Clone, serde::Deserialize, serde::Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct InstallPluginResponse {
    pub manifest: PluginManifest,
}

/// Metadata for an available plugin and its installation archive.
#[derive(Debug, Clone, serde::Deserialize, serde::Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct PluginCatalogEntry {
    pub id: String,
    pub name: String,
    pub version: String,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub publisher: String,
    #[serde(default)]
    pub license: String,
    #[serde(default)]
    pub dependencies: Vec<crate::manifest::ServiceRequirement>,
    #[serde(default)]
    pub metadata: HashMap<String, serde_json::Value>,
    #[serde(default)]
    pub requires: crate::manifest::PluginRequires,
    #[serde(default)]
    #[cfg_attr(feature = "openapi", schema(required = true))]
    pub url: String,
}
