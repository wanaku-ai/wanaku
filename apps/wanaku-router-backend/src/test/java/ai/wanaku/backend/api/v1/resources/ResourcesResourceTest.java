package ai.wanaku.backend.api.v1.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.NoOidcTestProfile;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class ResourcesResourceTest extends AbstractResourcesResourceTest {}
