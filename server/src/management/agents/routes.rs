pub(super) enum AgentRoute<'a> {
    List,
    Create,
    Get(&'a str, &'a str),
    Update(&'a str, &'a str),
    Delete(&'a str, &'a str),
    NotFound,
}

pub(super) fn resolve<'a>(method: &str, path: &'a str) -> AgentRoute<'a> {
    if path == "/api/v1/agents" {
        return match method {
            "GET" => AgentRoute::List,
            "POST" => AgentRoute::Create,
            _ => AgentRoute::NotFound,
        };
    }
    let Some(suffix) = path.strip_prefix("/api/v1/agents/") else {
        return AgentRoute::NotFound;
    };
    let Some((namespace, name)) = suffix.split_once('/') else {
        return AgentRoute::NotFound;
    };
    if namespace.is_empty() || name.is_empty() || name.contains('/') {
        return AgentRoute::NotFound;
    }
    match method {
        "GET" => AgentRoute::Get(namespace, name),
        "PUT" => AgentRoute::Update(namespace, name),
        "DELETE" => AgentRoute::Delete(namespace, name),
        _ => AgentRoute::NotFound,
    }
}
