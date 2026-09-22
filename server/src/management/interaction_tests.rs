use super::dispatch;

use wanaku_feature_intercept::InterceptFeature;
use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::feature::{Feature, HttpContext};

#[tokio::test]
async fn dispatch_interaction_routes_return_404() {
    let registry = InMemoryRegistry::new();
    let headers = http::HeaderMap::new();
    let features: Vec<Box<dyn Feature>> = vec![Box::new(InterceptFeature::new())];

    for method in ["GET", "DELETE"] {
        let ctx = HttpContext::new(method, "/api/v1/interactions", None, None, &headers);

        let resp = dispatch(&ctx, &registry, &features).await;

        assert_eq!(resp.status(), 404, "{method} must not expose interactions");
    }
}
