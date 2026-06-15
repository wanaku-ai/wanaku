/*
 * Copyright 2026 Wanaku AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.wanaku.backend.api.v1.oidc;

import java.net.URI;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the OIDC well-known forwarder target is pinned to the configured proxy and is not
 * influenced by the inbound request (SSRF guard).
 */
class OAuthAuthorizationServerWellKnownResourceTest {

    @Test
    void proxyBaseUriUsesConfiguredAuthProxy() {
        OAuthAuthorizationServerWellKnownResource resource = new OAuthAuthorizationServerWellKnownResource();
        resource.authProxy = "http://localhost:8543";

        assertEquals(URI.create("http://localhost:8543/"), resource.proxyBaseUri());
    }

    @Test
    void proxyBaseUriKeepsExistingTrailingSlash() {
        OAuthAuthorizationServerWellKnownResource resource = new OAuthAuthorizationServerWellKnownResource();
        resource.authProxy = "http://internal-proxy:9000/";

        assertEquals(URI.create("http://internal-proxy:9000/"), resource.proxyBaseUri());
    }

    @Test
    void proxyBaseUriFallsBackToLoopbackWhenUnset() {
        OAuthAuthorizationServerWellKnownResource resource = new OAuthAuthorizationServerWellKnownResource();
        resource.authProxy = "   ";

        assertEquals(URI.create("http://localhost:8080/"), resource.proxyBaseUri());
    }
}
