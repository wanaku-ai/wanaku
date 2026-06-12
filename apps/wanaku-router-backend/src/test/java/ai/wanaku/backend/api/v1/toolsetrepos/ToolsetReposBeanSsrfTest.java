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
package ai.wanaku.backend.api.v1.toolsetrepos;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the SSRF guards in {@link ToolsetReposBean}. These exercise the static
 * validation helpers directly so they require neither CDI nor outbound network access — every
 * blocked case below uses an IP literal or a malformed input, so the host resolution is purely
 * local.
 */
class ToolsetReposBeanSsrfTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://127.0.0.1/index.properties",
                "http://127.0.0.5/index.properties",
                "https://169.254.169.254/latest/meta-data/", // cloud metadata
                "http://10.0.0.1/",
                "http://192.168.1.1/",
                "http://172.16.0.1/",
                "http://100.100.100.200/", // carrier-grade NAT metadata
                "http://[::1]/", // IPv6 loopback literal
                "http://0.0.0.0/",
                "ftp://example.com/", // non-http scheme
                "file:///etc/passwd"
            })
    void rejectsUnsafeFetchTargets(String url) {
        assertThrows(WanakuException.class, () -> ToolsetReposBean.validateFetchTarget(url));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../../../etc/passwd",
                "foo/bar",
                "foo/../../bar",
                "name with space",
                "name?x=1",
                "name#frag",
                "name:1234",
                ".."
            })
    void rejectsUnsafeToolsetNames(String name) {
        assertThrows(WanakuException.class, () -> ToolsetReposBean.validateToolsetName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"my-toolset", "weather_v2", "Search.Tools", "abc123"})
    void acceptsWellFormedToolsetNames(String name) {
        assertDoesNotThrow(() -> ToolsetReposBean.validateToolsetName(name));
    }
}
