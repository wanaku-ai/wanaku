pub(crate) enum SimulationRoute<'a> {
    Validate,
    Simulate,
    Replay,
    Get(&'a str),
    Cancel(&'a str),
    Delete(&'a str),
    NotFound,
}
pub(crate) fn resolve<'a>(method: &str, path: &'a str) -> SimulationRoute<'a> {
    match (method, path) {
        ("POST", "/api/v1/policy-simulations/validate") => SimulationRoute::Validate,
        ("POST", "/api/v1/policy-simulations/simulate") => SimulationRoute::Simulate,
        ("POST", "/api/v1/policy-simulations/replays") => SimulationRoute::Replay,
        _ => {
            let Some(rest) = path.strip_prefix("/api/v1/policy-simulations/replays/") else {
                return SimulationRoute::NotFound;
            };
            match method {
                "GET" if !rest.contains('/') => SimulationRoute::Get(rest),
                "DELETE" if !rest.contains('/') => SimulationRoute::Delete(rest),
                "POST" => rest
                    .strip_suffix("/cancel")
                    .map_or(SimulationRoute::NotFound, SimulationRoute::Cancel),
                _ => SimulationRoute::NotFound,
            }
        }
    }
}
