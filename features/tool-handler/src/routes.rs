#[derive(Debug, PartialEq, Eq)]
pub(crate) enum HandlerRoute<'a> {
    List,
    Replace,
    Get(&'a str, &'a str),
    Delete(&'a str, &'a str),
    NotFound,
}

pub(crate) fn resolve<'a>(method: &str, path: &'a str) -> HandlerRoute<'a> {
    let Some(suffix) = path.strip_prefix("/api/v1/tool-handlers") else {
        return HandlerRoute::NotFound;
    };
    if suffix.is_empty() || suffix == "/" {
        return match method {
            "GET" => HandlerRoute::List,
            "PUT" => HandlerRoute::Replace,
            _ => HandlerRoute::NotFound,
        };
    }
    let Some((namespace, name)) = suffix
        .strip_prefix('/')
        .and_then(|suffix| suffix.split_once('/'))
    else {
        return HandlerRoute::NotFound;
    };
    if namespace.is_empty() || name.is_empty() || name.contains('/') {
        return HandlerRoute::NotFound;
    }
    match method {
        "GET" => HandlerRoute::Get(namespace, name),
        "DELETE" => HandlerRoute::Delete(namespace, name),
        _ => HandlerRoute::NotFound,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn routes_require_exact_resource_identity() {
        assert_eq!(
            resolve("PUT", "/api/v1/tool-handlers"),
            HandlerRoute::Replace
        );
        assert_eq!(
            resolve("GET", "/api/v1/tool-handlers/a/b"),
            HandlerRoute::Get("a", "b")
        );
        assert_eq!(
            resolve("DELETE", "/api/v1/tool-handlers/a/b/c"),
            HandlerRoute::NotFound
        );
        assert_eq!(
            resolve("PUT", "/api/v1/tool-handlers-extra"),
            HandlerRoute::NotFound
        );
    }
}
