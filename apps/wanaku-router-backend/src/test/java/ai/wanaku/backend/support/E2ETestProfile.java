package ai.wanaku.backend.support;

import java.util.Set;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for e2e capability tests.
 * Enables real resolvers that delegate to gRPC capabilities instead of the NoOp resolvers
 * used by other tests.
 */
public class E2ETestProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(E2ETestProvider.class);
    }
}
