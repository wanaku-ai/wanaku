package ai.wanaku.backend.api.v1.servicecatalog;

import java.util.Collection;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ForageDependencyResolverTest {

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "postgresql", "artemis"})
    void testResolveGav(String value) {
        ForageDependencyResolver resolver = new ForageDependencyResolver();

        final Collection<String> gavs = resolver.resolveGavs(value);
        Assertions.assertFalse(gavs.isEmpty());
        Assertions.assertTrue(gavs.size() > 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc", "jms"})
    void testResolveFactoryGav(String value) {
        ForageDependencyResolver resolver = new ForageDependencyResolver();

        final Optional<String> factoryGav = resolver.resolveFactoryGav(value);
        Assertions.assertFalse(factoryGav.isEmpty());
    }
}
