package ai.wanaku.backend.api.v1.oidc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.support.DegradedOidcTestProfile;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(DegradedOidcTestProfile.class)
class OidcDegradedStartupTest {

    @Test
    void metadataEndpointReturns503WhenOidcProviderUnavailable() {
        given().when().get("/q/oidc/.well-known/openid-configuration").then().statusCode(503);
    }

    @Test
    void tokenEndpointReturns503WhenOidcProviderUnavailable() {
        given().contentType("application/x-www-form-urlencoded")
                .when()
                .post("/q/oidc/token")
                .then()
                .statusCode(503);
    }

    @Test
    void authorizationEndpointReturns503WhenOidcProviderUnavailable() {
        given().when().get("/q/oidc/authorize").then().statusCode(503);
    }
}
