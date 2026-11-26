//! Startup validation of staged connector definitions.

use std::collections::{BTreeMap, HashSet};

use serde::de::DeserializeOwned;
use serde_yaml::Value;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::connectors::{ConnectorInstance, ConnectorProfile, ProfileVersion};
use wanaku_types::registry::{
    BindingRegistry, ForwardRegistry, NamespaceEntry, NamespaceRegistry, validate_namespace_name,
};

pub(crate) fn validate(root: &Value, registry: &InMemoryRegistry) -> Result<(), String> {
    let custom = definitions::<ConnectorProfile>(root, "connector_profiles")?;
    let instances = definitions::<ConnectorInstance>(root, "connector_instances")?;
    if custom.is_empty() && instances.is_empty() {
        return Ok(());
    }

    let profile_count = custom.len();
    let profiles = profiles_by_version(custom)?;
    let mut names = HashSet::new();
    for (index, instance) in instances.iter().enumerate() {
        validate_instance(instance, &profiles, registry)
            .map_err(|error| format!("connector_instances[{index}]: {error}"))?;
        if !names.insert(&instance.name) {
            return Err(format!(
                "connector_instances[{index}]: duplicate instance name"
            ));
        }
    }

    tracing::info!(
        custom_profiles = profile_count,
        instances = instances.len(),
        "staged connector definitions validated; connector activation is unavailable"
    );
    Ok(())
}

fn definitions<T: DeserializeOwned>(root: &Value, section: &str) -> Result<Vec<T>, String> {
    let Some(value) = root.get(section) else {
        return Ok(Vec::new());
    };
    let entries = value
        .as_sequence()
        .ok_or_else(|| format!("{section} must be a sequence"))?;
    entries
        .iter()
        .enumerate()
        .map(|(index, entry)| {
            serde_yaml::from_value(entry.clone()).map_err(|_| {
                format!("{section}[{index}]: invalid definition; check field names and types")
            })
        })
        .collect()
}

fn profiles_by_version(
    custom: Vec<ConnectorProfile>,
) -> Result<BTreeMap<ProfileVersion, ConnectorProfile>, String> {
    let builtin = ConnectorProfile::generic_mcp();
    let mut profiles = BTreeMap::new();
    for (index, profile) in custom.into_iter().enumerate() {
        profile
            .validate()
            .map_err(|error| format!("connector_profiles[{index}]: {error}"))?;
        if profile.identity.name == builtin.identity.name {
            return Err(format!(
                "connector_profiles[{index}]: the built-in profile name is reserved"
            ));
        }
        if profiles.insert(profile.identity.clone(), profile).is_some() {
            return Err(format!(
                "connector_profiles[{index}]: duplicate profile name and version"
            ));
        }
    }
    profiles.insert(builtin.identity.clone(), builtin);
    Ok(profiles)
}

fn validate_instance(
    instance: &ConnectorInstance,
    profiles: &BTreeMap<ProfileVersion, ConnectorProfile>,
    registry: &InMemoryRegistry,
) -> Result<(), String> {
    let profile = profiles
        .get(&instance.profile)
        .ok_or_else(|| "selects an unknown profile version".to_owned())?;
    profile
        .validate_bindings(instance, |id| registry.get_binding(id))
        .map_err(|error| error.to_string())?;
    validate_namespace_name(&instance.namespace)?;
    if registry
        .get_namespace(&instance.namespace)
        .as_ref()
        .is_some_and(NamespaceEntry::is_virtual)
    {
        return Err("virtual namespaces cannot own connector instances".to_owned());
    }
    if registry.get_forward(&instance.name).is_some() {
        return Err("instance name is already in use by a forward".to_owned());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::validate;
    use wanaku_infra::registry::InMemoryRegistry;

    #[test]
    fn rejects_duplicate_profile_versions_and_reserved_builtin_names() {
        let registry = InMemoryRegistry::new();
        let duplicate = r#"
connector_profiles:
  - &profile
    identity: {name: partner, version: 2}
    provider_key: partner
    transport: mcp_streamable_http
  - *profile
"#;
        let config = serde_yaml::from_str(duplicate).unwrap();
        let error = validate(&config, &registry).unwrap_err();
        assert!(
            error.contains("connector_profiles[1]: duplicate"),
            "{error}"
        );

        let reserved = duplicate.replace("name: partner", "name: generic-mcp");
        let config = serde_yaml::from_str(&reserved).unwrap();
        let error = validate(&config, &registry).unwrap_err();
        assert!(error.contains("connector_profiles[0]"), "{error}");
        assert!(
            error.contains("built-in profile name is reserved"),
            "{error}"
        );
    }

    #[test]
    fn malformed_connector_definitions_do_not_echo_submitted_values() {
        let registry = InMemoryRegistry::new();
        let instances = r#"
  - profile: {name: generic-mcp, version: 1}
    name: partner
    namespace: default
    address: https://example.com/mcp
    secret_value: submitted-secret
"#;
        let profiles = r#"
  - identity: {name: partner, version: 1}
    provider_key: partner
    transport: mcp_streamable_http
    credentials:
      - purpose: discovery
        allowed_mechanisms: [{type: bearer, secret_value: submitted-secret}]
"#;
        for (section, entries) in [
            ("connector_instances", instances),
            ("connector_profiles", profiles),
        ] {
            let config = serde_yaml::from_str(&format!("{section}:{entries}")).unwrap();
            let error = validate(&config, &registry).unwrap_err();
            assert!(error.starts_with(&format!("{section}[0]")), "{error}");
            assert!(!error.contains("submitted-secret"), "{error}");
        }
    }
}
