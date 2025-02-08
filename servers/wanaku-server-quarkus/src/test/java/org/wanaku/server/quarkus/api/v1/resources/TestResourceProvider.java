package org.wanaku.server.quarkus.api.v1.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class TestResourceProvider {

    @Produces
    TestResourceResolver ResourceProvider() {
        return new TestResourceResolver();
    }
}
