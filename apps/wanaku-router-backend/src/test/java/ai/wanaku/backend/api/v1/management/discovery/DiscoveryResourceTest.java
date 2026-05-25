package ai.wanaku.backend.api.v1.management.discovery;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class DiscoveryResourceTest extends AbstractDiscoveryResourceTest {}
