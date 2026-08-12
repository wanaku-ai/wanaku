use rand::RngExt;

const BASE62: &[u8] = b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const ID_LEN: usize = 8;

pub fn generate_short_id() -> String {
    let mut rng = rand::rng();
    let mut id = String::with_capacity(3 + ID_LEN);
    id.push_str("wk-");
    for _ in 0..ID_LEN {
        let idx = rng.random_range(0..BASE62.len());
        id.push(BASE62[idx] as char);
    }
    id
}

pub const REQUEST_ID_ARG: &str = "x-request-id";

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn id_has_correct_format() {
        let id = generate_short_id();
        assert!(id.starts_with("wk-"));
        assert_eq!(id.len(), 3 + ID_LEN);
    }

    #[test]
    fn ids_are_unique() {
        let a = generate_short_id();
        let b = generate_short_id();
        assert_ne!(a, b);
    }
}
