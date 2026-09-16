//! Outbound credential injection mechanisms and header-name validation.
//!
//! The broker supports a deliberately small set of injection mechanisms so a
//! caller can never select an arbitrary destination, header name, or injection
//! location at request time. Query-parameter, URL-path, request-body, and
//! arbitrary template injection are intentionally unsupported.

use std::str::FromStr;

use http::HeaderName;
use serde::{Deserialize, Serialize};

use super::secret::SecretMaterial;

/// A resolved credential header ready to inject into an upstream request.
///
/// The value is kept as [`SecretMaterial`] so it retains the redaction and
/// zeroization guarantees until the transport layer consumes it.
pub struct CredentialHeader {
    name: HeaderName,
    value: SecretMaterial,
}

impl CredentialHeader {
    /// The lower-cased header name. Safe to log.
    #[must_use]
    pub fn name(&self) -> &str {
        self.name.as_str()
    }

    /// The header name as an [`http::HeaderName`].
    #[must_use]
    pub const fn header_name(&self) -> &HeaderName {
        &self.name
    }

    /// Borrow the secret value. Callers must not log or persist the result.
    #[must_use]
    pub const fn expose_value(&self) -> &SecretMaterial {
        &self.value
    }
}

impl std::fmt::Debug for CredentialHeader {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CredentialHeader")
            .field("name", &self.name.as_str())
            .field("value", &"[REDACTED]")
            .finish()
    }
}

/// The supported outbound credential injection mechanisms.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum InjectionMechanism {
    /// `Authorization: Bearer <token>`.
    Bearer,
    /// A static named header carrying the resolved secret as its value.
    NamedHeader {
        /// The (validated) header name to inject.
        header: String,
    },
    /// HTTP Basic authentication with separately resolved username and password.
    Basic,
}

/// Errors produced while validating or applying an injection mechanism.
#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum InjectionError {
    /// The configured header name is not a syntactically valid HTTP header.
    #[error("invalid header name")]
    InvalidHeaderName,
    /// The configured header name is a hop-by-hop or routing-sensitive header.
    #[error("disallowed header name: {0}")]
    DisallowedHeader(String),
    /// The resolved secret value is not a valid header value.
    #[error("resolved credential is not a valid header value")]
    InvalidHeaderValue,
    /// Basic authentication requires exactly two secret references.
    #[error("basic authentication requires a username and a password secret")]
    BasicRequiresTwoSecrets,
}

/// Header names that must never carry broker-managed credentials because they
/// are hop-by-hop or affect routing/security decisions downstream.
const DISALLOWED_HEADERS: &[&str] = &[
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-connection",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
    "host",
    "content-length",
    "forwarded",
    "x-forwarded-for",
    "x-forwarded-host",
    "x-forwarded-proto",
];

/// Validate a configured credential header name.
///
/// Returns the normalized (lower-cased) [`HeaderName`] on success. Rejects
/// syntactically invalid names and hop-by-hop / routing-sensitive names.
pub fn validate_header_name(name: &str) -> Result<HeaderName, InjectionError> {
    let header = HeaderName::from_str(name).map_err(|_| InjectionError::InvalidHeaderName)?;
    if DISALLOWED_HEADERS.contains(&header.as_str()) {
        return Err(InjectionError::DisallowedHeader(header.as_str().to_owned()));
    }
    Ok(header)
}

impl InjectionMechanism {
    /// The header name(s) this mechanism will manage, for collision detection.
    ///
    /// Returns an error if a configured header name is invalid or disallowed.
    pub fn managed_header_names(&self) -> Result<Vec<HeaderName>, InjectionError> {
        match self {
            Self::Bearer | Self::Basic => Ok(vec![HeaderName::from_static("authorization")]),
            Self::NamedHeader { header } => Ok(vec![validate_header_name(header)?]),
        }
    }

    /// Validate the static configuration of this mechanism.
    pub fn validate(&self) -> Result<(), InjectionError> {
        self.managed_header_names().map(|_| ())
    }

    /// Build the credential header(s) for this mechanism from resolved secrets.
    ///
    /// * `Bearer` and `NamedHeader` consume a single secret.
    /// * `Basic` consumes exactly two secrets: username then password.
    pub fn build_headers(
        &self,
        secrets: &[SecretMaterial],
    ) -> Result<Vec<CredentialHeader>, InjectionError> {
        match self {
            Self::Bearer => build_bearer_header(secrets),
            Self::NamedHeader { header } => build_named_header(header, secrets),
            Self::Basic => build_basic_header(secrets),
        }
    }
}

fn build_bearer_header(
    secrets: &[SecretMaterial],
) -> Result<Vec<CredentialHeader>, InjectionError> {
    let secret = secrets.first().ok_or(InjectionError::InvalidHeaderValue)?;
    validate_secret(secret)?;
    let token = secret
        .expose_str()
        .ok_or(InjectionError::InvalidHeaderValue)?;
    let value = SecretMaterial::from_string(format!("Bearer {token}"));
    validate_header_value(value.expose_bytes())?;
    Ok(vec![CredentialHeader {
        name: HeaderName::from_static("authorization"),
        value,
    }])
}

fn build_named_header(
    header: &str,
    secrets: &[SecretMaterial],
) -> Result<Vec<CredentialHeader>, InjectionError> {
    let name = validate_header_name(header)?;
    let secret = secrets.first().ok_or(InjectionError::InvalidHeaderValue)?;
    validate_secret(secret)?;
    validate_header_value(secret.expose_bytes())?;
    Ok(vec![CredentialHeader {
        name,
        value: secret.clone(),
    }])
}

fn build_basic_header(secrets: &[SecretMaterial]) -> Result<Vec<CredentialHeader>, InjectionError> {
    if secrets.len() != 2 {
        return Err(InjectionError::BasicRequiresTwoSecrets);
    }
    validate_secret(&secrets[0])?;
    validate_secret(&secrets[1])?;
    let username = secrets[0]
        .expose_str()
        .ok_or(InjectionError::InvalidHeaderValue)?;
    let password = secrets[1]
        .expose_str()
        .ok_or(InjectionError::InvalidHeaderValue)?;
    let credentials = SecretMaterial::from_string(format!("{username}:{password}"));
    let encoded = SecretMaterial::from_string(base64_encode(credentials.expose_bytes()));
    let encoded = encoded
        .expose_str()
        .ok_or(InjectionError::InvalidHeaderValue)?;
    let value = SecretMaterial::from_string(format!("Basic {encoded}"));
    validate_header_value(value.expose_bytes())?;
    Ok(vec![CredentialHeader {
        name: HeaderName::from_static("authorization"),
        value,
    }])
}

const fn validate_secret(secret: &SecretMaterial) -> Result<(), InjectionError> {
    if secret.is_empty() {
        return Err(InjectionError::InvalidHeaderValue);
    }
    Ok(())
}

fn validate_header_value(bytes: &[u8]) -> Result<(), InjectionError> {
    http::HeaderValue::from_bytes(bytes)
        .map(|_| ())
        .map_err(|_| InjectionError::InvalidHeaderValue)
}

/// Minimal, dependency-free standard base64 encoder (used for Basic auth).
fn base64_encode(input: &[u8]) -> String {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(input.len().div_ceil(3) * 4);
    for chunk in input.chunks(3) {
        let b0 = chunk[0] as usize;
        let b1 = chunk.get(1).copied().unwrap_or(0) as usize;
        let b2 = chunk.get(2).copied().unwrap_or(0) as usize;
        let triple = (b0 << 16) | (b1 << 8) | b2;
        out.push(ALPHABET[(triple >> 18) & 0x3F] as char);
        out.push(ALPHABET[(triple >> 12) & 0x3F] as char);
        out.push(if chunk.len() > 1 {
            ALPHABET[(triple >> 6) & 0x3F] as char
        } else {
            '='
        });
        out.push(if chunk.len() > 2 {
            ALPHABET[triple & 0x3F] as char
        } else {
            '='
        });
    }
    out
}

/// Detect collisions between client-forwarded headers and broker-managed
/// credential headers. A collision means the caller could observe or influence
/// a broker-managed credential header, so it must be rejected.
///
/// `client_headers` is the set of lower-cased header names the client forwards.
pub fn detect_collisions<'a, I>(
    mechanism: &InjectionMechanism,
    client_headers: I,
) -> Result<(), InjectionError>
where
    I: IntoIterator<Item = &'a str>,
{
    let managed = mechanism.managed_header_names()?;
    for client in client_headers {
        let lower = client.to_ascii_lowercase();
        if managed.iter().any(|m| m.as_str() == lower) {
            return Err(InjectionError::DisallowedHeader(lower));
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_hop_by_hop_headers() {
        assert_eq!(
            validate_header_name("Connection"),
            Err(InjectionError::DisallowedHeader("connection".to_owned()))
        );
        assert_eq!(
            validate_header_name("Host"),
            Err(InjectionError::DisallowedHeader("host".to_owned()))
        );
    }

    #[test]
    fn rejects_invalid_header_name() {
        assert_eq!(
            validate_header_name("bad header"),
            Err(InjectionError::InvalidHeaderName)
        );
    }

    #[test]
    fn accepts_custom_header_name() {
        let name = validate_header_name("X-Api-Key").unwrap();
        assert_eq!(name.as_str(), "x-api-key");
    }

    #[test]
    fn bearer_builds_authorization_header() {
        let headers = InjectionMechanism::Bearer
            .build_headers(&[SecretMaterial::from("tok")])
            .unwrap();
        assert_eq!(headers.len(), 1);
        assert_eq!(headers[0].name(), "authorization");
        assert_eq!(headers[0].expose_value().expose_str(), Some("Bearer tok"));
    }

    #[test]
    fn named_header_uses_configured_name() {
        let mech = InjectionMechanism::NamedHeader {
            header: "X-Api-Key".to_owned(),
        };
        let headers = mech.build_headers(&[SecretMaterial::from("k")]).unwrap();
        assert_eq!(headers[0].name(), "x-api-key");
        assert_eq!(headers[0].expose_value().expose_str(), Some("k"));
    }

    #[test]
    fn named_header_rejects_disallowed_name() {
        let mech = InjectionMechanism::NamedHeader {
            header: "Host".to_owned(),
        };
        assert!(mech.build_headers(&[SecretMaterial::from("k")]).is_err());
    }

    #[test]
    fn basic_encodes_username_and_password() {
        let headers = InjectionMechanism::Basic
            .build_headers(&[SecretMaterial::from("user"), SecretMaterial::from("pass")])
            .unwrap();
        // base64("user:pass") == "dXNlcjpwYXNz"
        assert_eq!(
            headers[0].expose_value().expose_str(),
            Some("Basic dXNlcjpwYXNz")
        );
    }

    #[test]
    fn basic_requires_two_secrets() {
        // `CredentialHeader` deliberately does not implement `PartialEq` (it
        // holds secret material), so match on the error variant instead.
        assert!(matches!(
            InjectionMechanism::Basic.build_headers(&[SecretMaterial::from("only")]),
            Err(InjectionError::BasicRequiresTwoSecrets)
        ));
    }

    #[test]
    fn rejects_empty_secret_material() {
        let empty = [SecretMaterial::from("")];
        assert!(matches!(
            InjectionMechanism::Bearer.build_headers(&empty),
            Err(InjectionError::InvalidHeaderValue)
        ));
        assert!(matches!(
            InjectionMechanism::NamedHeader {
                header: "X-Api-Key".to_owned()
            }
            .build_headers(&empty),
            Err(InjectionError::InvalidHeaderValue)
        ));
        assert!(matches!(
            InjectionMechanism::Basic
                .build_headers(&[SecretMaterial::from("user"), SecretMaterial::from("")]),
            Err(InjectionError::InvalidHeaderValue)
        ));
    }

    #[test]
    fn detects_client_header_collision() {
        let mech = InjectionMechanism::Bearer;
        let result = detect_collisions(&mech, ["Authorization", "x-request-id"]);
        assert_eq!(
            result,
            Err(InjectionError::DisallowedHeader("authorization".to_owned()))
        );
    }

    #[test]
    fn no_collision_when_disjoint() {
        let mech = InjectionMechanism::NamedHeader {
            header: "X-Api-Key".to_owned(),
        };
        assert!(detect_collisions(&mech, ["authorization", "x-request-id"]).is_ok());
    }
}
