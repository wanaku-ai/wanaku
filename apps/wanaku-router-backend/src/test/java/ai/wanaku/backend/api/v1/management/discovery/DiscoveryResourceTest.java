package ai.wanaku.backend.api.v1.management.discovery;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class DiscoveryResourceTest extends AbstractDiscoveryResourceTest {}
