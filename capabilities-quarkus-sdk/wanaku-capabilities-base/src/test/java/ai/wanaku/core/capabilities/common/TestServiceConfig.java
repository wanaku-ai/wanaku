package ai.wanaku.core.capabilities.common;

import java.util.Map;
import java.util.Set;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;

/**
 * Minimal test implementation of WanakuServiceConfig for unit testing.
 */
class TestServiceConfig implements WanakuServiceConfig {
    private final String name;
    private final String serviceHome;

    TestServiceConfig(String name, String serviceHome) {
        this.name = name;
        this.serviceHome = serviceHome;
    }

    @Override
    public String serviceHome() {
        return serviceHome;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String baseUri() {
        return "%s://%s";
    }

    @Override
    public Service service() {
        return new Service() {
            @Override
            public Map<String, String> defaults() {
                return Map.of();
            }

            @Override
            public Set<Property> properties() {
                return Set.of();
            }
        };
    }

    @Override
    public Registration registration() {
        return new Registration() {
            @Override
            public String interval() {
                return "10s";
            }

            @Override
            public int delaySeconds() {
                return 5;
            }

            @Override
            public int retries() {
                return 12;
            }

            @Override
            public int retryWaitSeconds() {
                return 5;
            }

            @Override
            public String uri() {
                return "http://localhost:8080";
            }

            @Override
            public String announceAddress() {
                return "auto";
            }
        };
    }
}
