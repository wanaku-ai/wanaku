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

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KeycloakTokenProxyResourceTest {

    @Test
    void rejectsRealmMismatch() {
        KeycloakTokenProxyResource resource = new KeycloakTokenProxyResource();
        resource.authServer = "http://keycloak:8080";
        resource.configuredRealm = "wanaku";

        Response response = resource.proxyToken("other-realm", null, "grant_type=client_credentials");

        assertEquals(404, response.getStatus());
    }
}
