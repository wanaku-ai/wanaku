package ai.wanaku.backend.api.v1.oidc;

import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenIdConfigurationForwarderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsOpenIdConfigurationWhenUpstreamReturns200() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/q/oidc/.well-known/openid-configuration", exchange -> {
            String body = "{\"issuer\":\"http://example\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();

        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        OpenIdConfigurationForwarder forwarder = new OpenIdConfigurationForwarder(HttpClient.newHttpClient());

        Response response = forwarder.forward(baseUri, "/q/oidc");

        assertEquals(200, response.getStatus());
        assertEquals("{\"issuer\":\"http://example\"}", response.getEntity());
    }

    @Test
    void propagatesNonSuccessStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/q/oidc/.well-known/openid-configuration", exchange -> {
            String body = "{\"error\":\"boom\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();

        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        OpenIdConfigurationForwarder forwarder = new OpenIdConfigurationForwarder(HttpClient.newHttpClient());

        Response response = forwarder.forward(baseUri, "/q/oidc");

        assertEquals(500, response.getStatus());
        assertEquals("{\"error\":\"boom\"}", response.getEntity());
    }

    @Test
    void returnsBadGatewayWhenUpstreamIsUnavailable() throws Exception {
        int port = reserveUnusedPort();
        URI baseUri = URI.create("http://127.0.0.1:" + port + "/");
        OpenIdConfigurationForwarder forwarder = new OpenIdConfigurationForwarder(HttpClient.newHttpClient());

        Response response = forwarder.forward(baseUri, "/q/oidc");

        assertEquals(502, response.getStatus());
        assertNotNull(response);
    }

    private static int reserveUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
