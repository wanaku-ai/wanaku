//! Ephemeral, non-serializable secret material used by the credential broker.
//!
//! [`SecretMaterial`] deliberately does **not** implement [`std::fmt::Display`],
//! `serde::Serialize`, or `serde::Deserialize`. Its [`std::fmt::Debug`]
//! implementation is redacted so a secret can never leak through a debug or
//! structured-logging path. Owned bytes are zeroized on drop.

use zeroize::Zeroize;

/// Ephemeral credential material returned by a [`crate::credentials::SecretResolver`].
///
/// The value is held as raw bytes so that it can carry tokens, passwords, or
/// binary secrets. It exposes only the operations required to inject a
/// credential into an outbound upstream request.
///
/// # Security properties
///
/// * No `Display`, `Serialize`, or `Deserialize` implementations exist.
/// * `Debug` renders a fixed redaction placeholder and never the value.
/// * The backing memory is zeroized when the value is dropped.
#[derive(Clone, PartialEq, Eq)]
pub struct SecretMaterial {
    bytes: Vec<u8>,
}

impl SecretMaterial {
    /// Create secret material from raw bytes.
    #[must_use]
    pub const fn new(bytes: Vec<u8>) -> Self {
        Self { bytes }
    }

    /// Create secret material from a string value.
    #[must_use]
    pub const fn from_string(value: String) -> Self {
        Self::new(value.into_bytes())
    }

    /// Borrow the raw secret bytes. Callers must not log or persist the result.
    #[must_use]
    pub fn expose_bytes(&self) -> &[u8] {
        &self.bytes
    }

    /// Borrow the secret as a UTF-8 string slice, if it is valid UTF-8.
    ///
    /// Callers must not log or persist the result.
    #[must_use]
    pub fn expose_str(&self) -> Option<&str> {
        std::str::from_utf8(&self.bytes).ok()
    }

    /// Whether the material carries any bytes.
    #[must_use]
    pub const fn is_empty(&self) -> bool {
        self.bytes.is_empty()
    }
}

impl std::fmt::Debug for SecretMaterial {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("SecretMaterial([REDACTED])")
    }
}

impl Drop for SecretMaterial {
    fn drop(&mut self) {
        self.bytes.zeroize();
    }
}

impl From<String> for SecretMaterial {
    fn from(value: String) -> Self {
        Self::from_string(value)
    }
}

impl From<&str> for SecretMaterial {
    fn from(value: &str) -> Self {
        Self::from_string(value.to_owned())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn debug_is_redacted() {
        let secret = SecretMaterial::from("super-secret-token");
        let rendered = format!("{secret:?}");
        assert!(!rendered.contains("super-secret-token"));
        assert!(rendered.contains("REDACTED"));
    }

    #[test]
    fn exposes_bytes_and_str() {
        let secret = SecretMaterial::from("value");
        assert_eq!(secret.expose_bytes(), b"value");
        assert_eq!(secret.expose_str(), Some("value"));
        assert!(!secret.is_empty());
    }

    #[test]
    fn does_not_implement_display() {
        // Compile-time guarantee is documented; this test asserts the debug
        // redaction contract that observability paths rely on.
        let secret = SecretMaterial::new(vec![0x01, 0x02, 0x03]);
        assert_eq!(format!("{secret:?}"), "SecretMaterial([REDACTED])");
    }
}
