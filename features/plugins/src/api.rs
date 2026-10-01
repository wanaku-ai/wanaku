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
