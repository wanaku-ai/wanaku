mod handlers;
mod routes;

pub(super) fn dispatch(
    ctx: &wanaku_types::feature::HttpContext<'_>,
    registry: &wanaku_infra::registry::InMemoryRegistry,
) -> Option<http::Response<Vec<u8>>> {
    use routes::AgentRoute;
    Some(match routes::resolve(ctx.method, ctx.path) {
        AgentRoute::List => handlers::list(registry, ctx.query),
        AgentRoute::Create => handlers::save(registry, ctx.body.unwrap_or(""), None),
        AgentRoute::Get(namespace, name) => handlers::get(registry, namespace, name),
        AgentRoute::Update(namespace, name) => {
            handlers::save(registry, ctx.body.unwrap_or(""), Some((namespace, name)))
        }
        AgentRoute::Delete(namespace, name) => handlers::delete(registry, namespace, name),
        AgentRoute::NotFound => return None,
    })
}
