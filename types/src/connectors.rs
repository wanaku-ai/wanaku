//! Declarative configuration shared by connector management and discovery.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::credentials::{
    CredentialBinding, CredentialPurpose, InjectionMechanism, NormalizedOrigin,
};

/// An immutable profile identity. Instances pin both components.
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ProfileVersion {
    pub name: String,
    pub version: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ConnectorProfile {
    pub identity: ProfileVersion,
    pub provider_key: String,
    #[serde(default)]
    pub drift_mode: DriftMode,
    pub transport: ConnectorTransport,
    #[serde(default)]
    pub address: AddressConstraints,
    #[serde(default)]
    pub configuration: BTreeMap<String, ConfigurationField>,
    #[serde(default)]
    pub credentials: Vec<CredentialRequirement>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
#[derive(Default)]
pub enum DriftMode {
    Allow,
    #[default]
    Deny,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ConnectorTransport {
    McpStreamableHttp,
}

/// Address restrictions. Origins, when specified, include a scheme and port.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct AddressConstraints {
    pub require_tls: bool,
    pub allow_query: bool,
    pub allowed_origins: Vec<String>,
}

/// A finite set of non-secret values accepted for one field.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ConfigurationField {
    pub choices: Vec<String>,
    pub default: Option<String>,
    #[serde(default)]
    pub required: bool,
    #[serde(default)]
    pub immutable: bool,
    #[serde(default)]
    pub rediscover: bool,
    #[serde(default)]
    pub revalidate_policy: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CredentialRequirement {
    pub purpose: CredentialPurpose,
    #[serde(deserialize_with = "deserialize_allowed_mechanisms")]
    pub allowed_mechanisms: Vec<InjectionMechanism>,
}

fn deserialize_allowed_mechanisms<'de, D>(
    deserializer: D,
) -> Result<Vec<InjectionMechanism>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    // Struct variants reject extra fields even for mechanisms with no settings.
    #[derive(Deserialize)]
    #[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
    enum StrictMechanism {
        Bearer {},
        NamedHeader { header: String },
        Basic {},
    }

    let mechanisms = Vec::<StrictMechanism>::deserialize(deserializer)?;
    Ok(mechanisms
        .into_iter()
        .map(|mechanism| match mechanism {
            StrictMechanism::Bearer {} => InjectionMechanism::Bearer,
            StrictMechanism::NamedHeader { header } => InjectionMechanism::NamedHeader { header },
            StrictMechanism::Basic {} => InjectionMechanism::Basic,
        })
        .collect())
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ConnectorInstance {
    pub profile: ProfileVersion,
    pub name: String,
    pub namespace: String,
    pub address: String,
    #[serde(default)]
    pub configuration: BTreeMap<String, String>,
    /// The IDs of brokerage bindings. Secret material is resolved by the broker.
    #[serde(default)]
    pub credential_bindings: BTreeMap<CredentialPurpose, String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ValueSource {
    Profile,
    Instance,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct EffectiveValue {
    pub value: String,
    pub source: ValueSource,
}

#[derive(Debug, PartialEq, Eq, thiserror::Error)]
#[error("profile {profile_name:?} version {version} field {field:?}: {constraint}")]
pub struct ProfileError {
    profile_name: String,
    version: u32,
    field: String,
    constraint: &'static str,
}

impl ConnectorProfile {
    #[must_use]
    pub fn generic_mcp() -> Self {
        Self {
            identity: ProfileVersion {
                name: "generic-mcp".to_owned(),
                version: 1,
            },
            transport: ConnectorTransport::McpStreamableHttp,
            provider_key: "generic-mcp".to_owned(),
            drift_mode: DriftMode::Deny,
            address: AddressConstraints {
                allow_query: true,
                ..AddressConstraints::default()
            },
            configuration: BTreeMap::new(),
            credentials: Vec::new(),
        }
    }

    pub fn validate(&self) -> Result<(), ProfileError> {
        if !identifier(&self.identity.name) || self.identity.version == 0 {
            return Err(self.error("identity", "requires a valid name and positive version"));
        }
        if !identifier(&self.provider_key) {
            return Err(self.error("provider_key", "requires a valid provider identifier"));
        }
        if self.configuration.len() > 32 || self.address.allowed_origins.len() > 32 {
            return Err(self.error("profile", "exceeds configuration or origin limit"));
        }
        self.validate_configuration()?;
        for origin in &self.address.allowed_origins {
            if !NormalizedOrigin::from_address(origin).is_ok_and(|value| value.as_str() == origin)
                || (self.address.require_tls && !origin.starts_with("https://"))
            {
                return Err(self.error("address", "requires a normalized permitted origin"));
            }
        }
        self.validate_credentials()
    }

    fn validate_configuration(&self) -> Result<(), ProfileError> {
        for (name, field) in &self.configuration {
            if !identifier(name)
                || field.choices.is_empty()
                || field.choices.len() > 32
                || field.choices.iter().any(|value| !identifier(value))
            {
                return Err(self.error("configuration", "invalid field name or enumeration"));
            }
            if field
                .default
                .as_ref()
                .is_some_and(|value| !field.choices.contains(value))
            {
                return Err(self.error(name, "default must be an allowed choice"));
            }
        }
        Ok(())
    }

    fn validate_credentials(&self) -> Result<(), ProfileError> {
        let mut purposes = std::collections::HashSet::new();
        for requirement in &self.credentials {
            if !purposes.insert(requirement.purpose)
                || requirement.allowed_mechanisms.is_empty()
                || requirement.allowed_mechanisms.len() > 16
                || requirement
                    .allowed_mechanisms
                    .iter()
                    .any(|mechanism| mechanism.validate().is_err())
            {
                return Err(self.error(
                    "credentials",
                    "requires unique purposes and 1 to 16 mechanisms per purpose",
                ));
            }
        }
        Ok(())
    }

    /// Validate the instance and compose its declared values without consulting a resolver.
    pub fn compose(
        &self,
        instance: &ConnectorInstance,
    ) -> Result<BTreeMap<String, EffectiveValue>, ProfileError> {
        self.validate()?;
        if self.identity != instance.profile {
            return Err(self.error("identity", "instance must select this profile version"));
        }
        if !identifier(&instance.name) || !identifier(&instance.namespace) {
            return Err(self.error("instance", "requires a valid name and namespace"));
        }
        self.validate_address(&instance.address)?;
        for requirement in &self.credentials {
            if !instance
                .credential_bindings
                .contains_key(&requirement.purpose)
            {
                return Err(self.error("credential_bindings", "missing required binding reference"));
            }
        }
        if instance
            .configuration
            .keys()
            .any(|key| !self.configuration.contains_key(key))
        {
            return Err(self.error("configuration", "contains an undeclared field"));
        }

        let mut effective = BTreeMap::new();
        for (name, field) in &self.configuration {
            let selected = match instance.configuration.get(name) {
                Some(_) if field.default.is_some() => {
                    return Err(self.error(name, "instance cannot override a profile default"));
                }
                Some(value) if field.choices.contains(value) => {
                    Some((value, ValueSource::Instance))
                }
                Some(_) => return Err(self.error(name, "value must be an allowed choice")),
                None => field
                    .default
                    .as_ref()
                    .map(|value| (value, ValueSource::Profile)),
            };
            match selected {
                Some((value, source)) => {
                    effective.insert(
                        name.clone(),
                        EffectiveValue {
                            value: value.clone(),
                            source,
                        },
                    );
                }
                None if field.required => return Err(self.error(name, "value is required")),
                None => {}
            }
        }
        Ok(effective)
    }

    /// Check brokerage metadata without resolving secret material.
    pub fn validate_bindings(
        &self,
        instance: &ConnectorInstance,
        mut lookup: impl FnMut(&str) -> Option<CredentialBinding>,
    ) -> Result<(), ProfileError> {
        self.compose(instance)?;
        for (&purpose, id) in &instance.credential_bindings {
            let incompatible =
                || self.error("credential_bindings", "binding is missing or incompatible");
            let binding = lookup(id).ok_or_else(incompatible)?;
            let namespaces = &binding.restrictions.namespaces;
            if binding.id != *id
                || binding.validate().is_err()
                || binding.forward_id != instance.name
                || !binding.origin.matches_address(&instance.address)
                || !binding.allowed_purposes.contains(&purpose)
                || (!namespaces.is_empty() && !namespaces.contains(&instance.namespace))
                || self.credentials.iter().any(|requirement| {
                    requirement.purpose == purpose
                        && !requirement.allowed_mechanisms.contains(&binding.mechanism)
                })
            {
                return Err(incompatible());
            }
        }
        Ok(())
    }

    /// Validate a move to a newer version of the same profile. A rollback
    /// requires publishing the earlier definition with a new version.
    pub fn validate_migration(
        &self,
        current: &Self,
        instance: &ConnectorInstance,
        candidate: &ConnectorInstance,
    ) -> Result<BTreeMap<String, EffectiveValue>, ProfileError> {
        if self.identity.name != current.identity.name
            || self.identity.version <= current.identity.version
            || candidate.name != instance.name
            || candidate.namespace != instance.namespace
            || candidate.address != instance.address
        {
            return Err(self.error(
                "migration",
                "requires a newer version and the same instance and destination",
            ));
        }
        let previous = current.compose(instance)?;
        let next = self.compose(candidate)?;
        for (name, field) in &current.configuration {
            if field.immutable
                && previous.get(name).map(|value| &value.value)
                    != next.get(name).map(|value| &value.value)
            {
                return Err(self.error(name, "immutable value cannot change"));
            }
        }
        Ok(next)
    }

    fn validate_address(&self, address: &str) -> Result<(), ProfileError> {
        let invalid = || self.error("address", "destination does not meet profile constraints");
        if address.len() > 2048 || address.contains(['#', '\\']) {
            return Err(invalid());
        }
        let uri = address.parse::<http::Uri>().map_err(|_| invalid())?;
        let origin = NormalizedOrigin::from_address(address).map_err(|_| invalid())?;
        if uri
            .authority()
            .is_some_and(|authority| authority.as_str().contains(['@', '%']))
            || (self.address.require_tls && uri.scheme_str() != Some("https"))
            || (!self.address.allow_query && uri.query().is_some())
            || (!self.address.allowed_origins.is_empty()
                && !self
                    .address
                    .allowed_origins
                    .iter()
                    .any(|allowed| allowed == origin.as_str()))
        {
            return Err(invalid());
        }
        Ok(())
    }

    fn error(&self, field: &str, constraint: &'static str) -> ProfileError {
        let name = &self.identity.name;
        ProfileError {
            profile_name: if identifier(name) {
                name.clone()
            } else {
                "<invalid>".to_owned()
            },
            version: self.identity.version,
            field: field.to_owned(),
            constraint,
        }
    }
}

fn identifier(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.'))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn instance(profile: &ConnectorProfile) -> ConnectorInstance {
        let profile = serde_json::to_string(&profile.identity).unwrap();
        serde_json::from_str(&format!(
            r#"{{"profile":{profile},"name":"example","namespace":"default","address":"https://example.org/mcp"}}"#
        ))
        .unwrap()
    }

    #[test]
    fn compose_tracks_sources_and_rejects_overrides_without_echoing_input() {
        let mut profile = ConnectorProfile::generic_mcp();
        profile.configuration.insert(
            "region".to_owned(),
            ConfigurationField {
                choices: vec!["east".to_owned(), "west".to_owned()],
                default: Some("east".to_owned()),
                required: true,
                immutable: true,
                rediscover: true,
                revalidate_policy: true,
            },
        );
        let mut instance = instance(&profile);
        assert_eq!(
            profile.compose(&instance).unwrap()["region"].source,
            ValueSource::Profile
        );
        instance
            .configuration
            .insert("region".to_owned(), "west".to_owned());
        assert!(profile.compose(&instance).is_err());
        profile.configuration.get_mut("region").unwrap().default = None;
        assert_eq!(
            profile.compose(&instance).unwrap()["region"].source,
            ValueSource::Instance
        );
        instance
            .configuration
            .insert("region".to_owned(), "submitted-token".to_owned());
        let message = profile.compose(&instance).unwrap_err().to_string();
        assert!(message.contains("region"));
        assert!(!message.contains("submitted-token"));
    }

    #[test]
    fn address_constraints_are_applied_before_composition() {
        let mut profile = ConnectorProfile::generic_mcp();
        profile.address = AddressConstraints {
            require_tls: true,
            allow_query: false,
            allowed_origins: vec!["https://example.org:443".to_owned()],
        };
        let mut instance = instance(&profile);
        assert!(profile.compose(&instance).is_ok());
        for address in [
            "http://example.org/mcp",
            "https://127.0.0.1/mcp",
            "https://user:pass@example.org/mcp",
            "https://example.org/mcp?key=value",
            "https://example.org:444/mcp",
            "https://example.org/mcp#part",
        ] {
            instance.address = address.to_owned();
            assert!(profile.compose(&instance).is_err(), "{address}");
        }
    }

    #[test]
    fn schema_is_closed_and_migration_rejects_downgrades() {
        let old = ConnectorProfile::generic_mcp();
        let mut invalid = serde_json::to_value(&old).unwrap();
        invalid["script"] = serde_json::json!("run");
        assert!(serde_json::from_value::<ConnectorProfile>(invalid).is_err());

        let mut new = old.clone();
        new.identity.version = 2;
        let current = instance(&old);
        let candidate = instance(&new);
        assert!(new.validate_migration(&old, &current, &candidate).is_ok());
        assert!(old.validate_migration(&new, &candidate, &current).is_err());
    }

    #[test]
    fn discovery_binding_must_match_instance_origin_purpose_and_mechanism() {
        let mut profile = ConnectorProfile::generic_mcp();
        let purpose = CredentialPurpose::Discovery;
        profile.credentials.push(CredentialRequirement {
            purpose,
            allowed_mechanisms: vec![InjectionMechanism::Bearer],
        });
        let mut instance = instance(&profile);
        assert!(profile.validate_bindings(&instance, |_| None).is_err());
        instance
            .credential_bindings
            .insert(purpose, "binding".to_owned());
        let mut binding = CredentialBinding {
            id: "binding".to_owned(),
            forward_id: instance.name.clone(),
            origin: NormalizedOrigin::from_address(&instance.address).unwrap(),
            mechanism: InjectionMechanism::Bearer,
            secret_refs: vec![crate::credentials::SecretRef::parse("env:TEST_TOKEN").unwrap()],
            allowed_purposes: vec![purpose],
            restrictions: Default::default(),
            cache: Default::default(),
            revision: 1,
        };
        assert!(
            profile
                .validate_bindings(&instance, |_| Some(binding.clone()))
                .is_ok()
        );
        binding.origin = NormalizedOrigin::from_address("https://other.example.org").unwrap();
        assert!(
            profile
                .validate_bindings(&instance, |_| Some(binding.clone()))
                .is_err()
        );
        binding.origin = NormalizedOrigin::from_address(&instance.address).unwrap();
        binding.allowed_purposes = vec![CredentialPurpose::Invocation];
        assert!(
            profile
                .validate_bindings(&instance, |_| Some(binding.clone()))
                .is_err()
        );
        binding.allowed_purposes = vec![purpose];
        binding.mechanism = InjectionMechanism::NamedHeader {
            header: "x-token".to_owned(),
        };
        assert!(
            profile
                .validate_bindings(&instance, |_| Some(binding.clone()))
                .is_err()
        );
        assert!(
            !serde_json::to_string(&instance)
                .unwrap()
                .contains("TEST_TOKEN")
        );
    }
}
